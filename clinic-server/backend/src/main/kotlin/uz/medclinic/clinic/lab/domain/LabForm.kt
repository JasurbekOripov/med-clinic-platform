package uz.medclinic.clinic.lab.domain

import uz.medclinic.platform.domain.FieldViolation
import uz.medclinic.platform.domain.Text
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * A lab result form ("shablon") is a list of rows the lab defines itself. The program contains no medical values:
 * names, units and every reference range are entered by the clinic's lab (owner's decision, patient safety).
 *
 * Reference ranges follow the usual partitioning of reference intervals (CLSI EP28; HL7 FHIR
 * ObservationDefinition.qualifiedInterval): by sex, by age, and by physiological state such as pregnancy.
 */
enum class RowKind { HEADER, NUMBER, CHOICE, POS_NEG, TEXT }

enum class RangeSex { ANY, MALE, FEMALE }

enum class AgeUnit { DAYS, MONTHS, YEARS }

/** NORMAL drives the ↑/↓ flags; CRITICAL marks dangerous values that need immediate attention. */
enum class RangeKind { NORMAL, CRITICAL }

/** States that change normal values and are not stored on the patient; the lab picks one when entering results. */
enum class PhysioState { NONE, PREGNANCY_T1, PREGNANCY_T2, PREGNANCY_T3, FOLLICULAR, OVULATION, LUTEAL, POSTMENOPAUSE, CUSTOM }

data class AgeBound(val value: Int, val unit: AgeUnit)

/** The patient-side facts a range is matched against. [customState] is used with [PhysioState.CUSTOM]. */
data class PatientContext(
    val sex: RangeSex,
    val birthDate: LocalDate,
    val on: LocalDate,
    val state: PhysioState = PhysioState.NONE,
    val customState: String? = null,
) {
    init {
        require(sex != RangeSex.ANY)
    }

    /** Completed days/months/years, as age is counted on birthdays (a 17-year-old turns 18 on the birthday). */
    fun ageIn(unit: AgeUnit): Long = when (unit) {
        AgeUnit.DAYS -> ChronoUnit.DAYS.between(birthDate, on)
        AgeUnit.MONTHS -> Period.between(birthDate, on).toTotalMonths()
        AgeUnit.YEARS -> Period.between(birthDate, on).years.toLong()
    }
}

data class ReferenceRange(
    val kind: RangeKind = RangeKind.NORMAL,
    val sex: RangeSex = RangeSex.ANY,
    /** Inclusive lower age bound; null = from birth. */
    val ageFrom: AgeBound? = null,
    /** Exclusive upper age bound ("0–18 years" means younger than 18); null = no upper bound. */
    val ageTo: AgeBound? = null,
    val state: PhysioState = PhysioState.NONE,
    val customState: String? = null,
    val low: BigDecimal? = null,
    val high: BigDecimal? = null,
    /** Printed norm for ranges that are not two numbers, e.g. "< 5.7" written by the lab, or "manfiy". */
    val text: String? = null,
) {
    fun matches(p: PatientContext): Boolean {
        if (sex != RangeSex.ANY && sex != p.sex) return false
        if (ageFrom != null && p.ageIn(ageFrom.unit) < ageFrom.value) return false
        if (ageTo != null && p.ageIn(ageTo.unit) >= ageTo.value) return false
        // A state-specific range applies only in that state; a NONE range applies to everyone (the general norm).
        if (state != PhysioState.NONE) {
            if (state != p.state) return false
            if (state == PhysioState.CUSTOM && !customState.equals(p.customState, ignoreCase = true)) return false
        }
        return true
    }

    /** Higher = narrower population; the most specific matching range is used. A function, so it is not serialised. */
    fun specificity(): Int =
        (if (state != PhysioState.NONE) 100 else 0) + (if (sex != RangeSex.ANY) 10 else 0) +
            (if (ageFrom != null) 1 else 0) + (if (ageTo != null) 1 else 0)

    /** Printed norm: the lab's text if given, otherwise "low – high", "≥ low" or "≤ high". */
    fun display(): String = text ?: when {
        low != null && high != null -> "${low.toPlainString()} – ${high.toPlainString()}"
        low != null -> "≥ ${low.toPlainString()}"
        high != null -> "≤ ${high.toPlainString()}"
        else -> ""
    }
}

data class ChoiceOption(val label: String, val normal: Boolean)

data class FormRow(
    /** Stable within the template across versions, so results and edits line up. */
    val id: UUID,
    val kind: RowKind,
    val name: String,
    val unit: String? = null,
    /** NUMBER: digits after the decimal point when printing. */
    val decimals: Int = 1,
    val options: List<ChoiceOption> = emptyList(),
    /** POS_NEG: which answer is normal (usually negative). */
    val normalPositive: Boolean = false,
    val ranges: List<ReferenceRange> = emptyList(),
) {
    /** The most specific matching range of [kind]; on a tie the first in the lab's order. */
    fun rangeFor(p: PatientContext, kind: RangeKind = RangeKind.NORMAL): ReferenceRange? =
        ranges.withIndex().filter { it.value.kind == kind && it.value.matches(p) }
            .maxWithOrNull(compareBy<IndexedValue<ReferenceRange>> { it.value.specificity() }.thenByDescending { it.index })?.value
}

enum class Flag { NORMAL, LOW, HIGH, CRITICAL_LOW, CRITICAL_HIGH, ABNORMAL, NO_RANGE }

object Evaluator {
    /** Flag for a numeric value: critical limits first, then the normal range; no matching range → NO_RANGE. */
    fun number(row: FormRow, value: BigDecimal, p: PatientContext): Flag {
        row.rangeFor(p, RangeKind.CRITICAL)?.let { c ->
            if (c.low != null && value < c.low) return Flag.CRITICAL_LOW
            if (c.high != null && value > c.high) return Flag.CRITICAL_HIGH
        }
        val r = row.rangeFor(p) ?: return Flag.NO_RANGE
        if (r.low == null && r.high == null) return Flag.NO_RANGE
        return when {
            r.low != null && value < r.low -> Flag.LOW
            r.high != null && value > r.high -> Flag.HIGH
            else -> Flag.NORMAL
        }
    }

    fun choice(row: FormRow, label: String): Flag =
        row.options.firstOrNull { it.label == label }?.let { if (it.normal) Flag.NORMAL else Flag.ABNORMAL } ?: Flag.ABNORMAL

    fun posNeg(row: FormRow, positive: Boolean): Flag = if (positive == row.normalPositive) Flag.NORMAL else Flag.ABNORMAL
}

/** Validation of a whole form; field paths like `rows[2].ranges[0].high` let the UI point at the exact cell. */
object FormValidator {
    private const val MAX_ROWS = 200
    private const val MAX_RANGES = 30

    fun validate(rows: List<FormRow>): List<FieldViolation> {
        val errors = mutableListOf<FieldViolation>()
        if (rows.none { it.kind != RowKind.HEADER }) errors += FieldViolation("rows", "lab.form_empty")
        if (rows.size > MAX_ROWS) errors += FieldViolation("rows", "validation.size", mapOf("max" to MAX_ROWS))
        if (rows.map { it.id }.toSet().size != rows.size) errors += FieldViolation("rows", "lab.duplicate_row")
        rows.forEachIndexed { i, row ->
            val at = "rows[$i]"
            if (Text.clean(row.name) == null) errors += FieldViolation("$at.name", "validation.not_blank")
            if (row.name.length > 200) errors += FieldViolation("$at.name", "validation.size", mapOf("max" to 200))
            if ((row.unit?.length ?: 0) > 30) errors += FieldViolation("$at.unit", "validation.size", mapOf("max" to 30))
            if (row.decimals !in 0..4) errors += FieldViolation("$at.decimals", "validation.range", mapOf("min" to 0, "max" to 4))
            if (row.kind == RowKind.CHOICE) {
                val labels = row.options.map { Text.clean(it.label) }
                if (labels.size < 2 || labels.any { it == null } || labels.toSet().size != labels.size) {
                    errors += FieldViolation("$at.options", "lab.choice_options")
                }
            }
            if (row.kind != RowKind.NUMBER && row.ranges.any { it.low != null || it.high != null }) {
                errors += FieldViolation("$at.ranges", "lab.ranges_only_for_numbers")
            }
            if (row.ranges.size > MAX_RANGES) errors += FieldViolation("$at.ranges", "validation.size", mapOf("max" to MAX_RANGES))
            row.ranges.forEachIndexed { j, r ->
                val rat = "$at.ranges[$j]"
                if (r.low == null && r.high == null && Text.clean(r.text) == null) errors += FieldViolation(rat, "lab.range_empty")
                if (r.low != null && r.high != null && r.low > r.high) errors += FieldViolation("$rat.high", "lab.range_low_above_high")
                if (r.ageFrom != null && r.ageFrom.value < 0) errors += FieldViolation("$rat.ageFrom", "lab.age_negative")
                if (r.ageTo != null && r.ageTo.value <= 0) errors += FieldViolation("$rat.ageTo", "lab.age_negative")
                if (r.ageFrom != null && r.ageTo != null && r.ageFrom.unit == r.ageTo.unit && r.ageFrom.value >= r.ageTo.value) {
                    errors += FieldViolation("$rat.ageTo", "lab.age_order")
                }
                if (r.state == PhysioState.CUSTOM && Text.clean(r.customState) == null) errors += FieldViolation("$rat.customState", "validation.not_blank")
                if (r.state in PREGNANCY_OR_CYCLE && r.sex == RangeSex.MALE) errors += FieldViolation("$rat.state", "lab.state_needs_female")
            }
        }
        return errors
    }

    private val PREGNANCY_OR_CYCLE = setOf(
        PhysioState.PREGNANCY_T1, PhysioState.PREGNANCY_T2, PhysioState.PREGNANCY_T3,
        PhysioState.FOLLICULAR, PhysioState.OVULATION, PhysioState.LUTEAL, PhysioState.POSTMENOPAUSE,
    )
}
