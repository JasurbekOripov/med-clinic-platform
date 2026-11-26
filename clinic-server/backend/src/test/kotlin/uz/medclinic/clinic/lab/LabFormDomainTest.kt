package uz.medclinic.clinic.lab

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uz.medclinic.clinic.lab.domain.AgeBound
import uz.medclinic.clinic.lab.domain.AgeUnit
import uz.medclinic.clinic.lab.domain.ChoiceOption
import uz.medclinic.clinic.lab.domain.Evaluator
import uz.medclinic.clinic.lab.domain.Flag
import uz.medclinic.clinic.lab.domain.FormRow
import uz.medclinic.clinic.lab.domain.FormValidator
import uz.medclinic.clinic.lab.domain.PatientContext
import uz.medclinic.clinic.lab.domain.PhysioState
import uz.medclinic.clinic.lab.domain.RangeKind
import uz.medclinic.clinic.lab.domain.RangeSex
import uz.medclinic.clinic.lab.domain.ReferenceRange
import uz.medclinic.clinic.lab.domain.RowKind
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** The numbers below are arbitrary test values, not medical reference ranges. */
class LabFormDomainTest {
    private val today = LocalDate.of(2026, 11, 26)
    private fun d(s: String) = BigDecimal(s)
    private fun years(n: Int) = AgeBound(n, AgeUnit.YEARS)

    private val row = FormRow(
        UUID.randomUUID(), RowKind.NUMBER, "Test X", "g/L",
        ranges = listOf(
            ReferenceRange(low = d("100"), high = d("200")), // general
            ReferenceRange(sex = RangeSex.FEMALE, ageFrom = years(18), low = d("110"), high = d("150")),
            ReferenceRange(sex = RangeSex.FEMALE, ageFrom = years(18), state = PhysioState.PREGNANCY_T2, low = d("105"), high = d("140")),
            ReferenceRange(ageTo = AgeBound(1, AgeUnit.MONTHS), low = d("140"), high = d("220")), // newborns
            ReferenceRange(kind = RangeKind.CRITICAL, low = d("60"), high = d("250")),
        ),
    )

    private fun ctx(sex: RangeSex, birth: LocalDate, state: PhysioState = PhysioState.NONE) = PatientContext(sex, birth, today, state)

    @Test
    fun `the most specific matching range wins`() {
        val woman = ctx(RangeSex.FEMALE, LocalDate.of(1990, 1, 1))
        assertThat(row.rangeFor(woman)?.low).isEqualTo(d("110"))
        assertThat(row.rangeFor(woman.copy(state = PhysioState.PREGNANCY_T2))?.low).isEqualTo(d("105"))
        assertThat(row.rangeFor(woman.copy(state = PhysioState.PREGNANCY_T1))?.low).isEqualTo(d("110")) // no T1 range: general female
        assertThat(row.rangeFor(ctx(RangeSex.MALE, LocalDate.of(1990, 1, 1)))?.low).isEqualTo(d("100"))
        assertThat(row.rangeFor(ctx(RangeSex.MALE, today.minusDays(10)))?.low).isEqualTo(d("140"))
    }

    @Test
    fun `age bounds use completed units with an exclusive upper bound`() {
        val girl17 = ctx(RangeSex.FEMALE, today.minusYears(18).plusDays(1))
        val woman18 = ctx(RangeSex.FEMALE, today.minusYears(18))
        assertThat(row.rangeFor(girl17)?.low).isEqualTo(d("100"))
        assertThat(row.rangeFor(woman18)?.low).isEqualTo(d("110"))
        val oneMonth = ctx(RangeSex.MALE, today.minusMonths(1))
        assertThat(row.rangeFor(oneMonth)?.low).isEqualTo(d("100"))
    }

    @Test
    fun `flags - critical before normal, no range is never guessed`() {
        val man = ctx(RangeSex.MALE, LocalDate.of(1980, 5, 5))
        assertThat(Evaluator.number(row, d("150"), man)).isEqualTo(Flag.NORMAL)
        assertThat(Evaluator.number(row, d("99.9"), man)).isEqualTo(Flag.LOW)
        assertThat(Evaluator.number(row, d("201"), man)).isEqualTo(Flag.HIGH)
        assertThat(Evaluator.number(row, d("59"), man)).isEqualTo(Flag.CRITICAL_LOW)
        assertThat(Evaluator.number(row, d("251"), man)).isEqualTo(Flag.CRITICAL_HIGH)
        val bare = row.copy(ranges = listOf(ReferenceRange(sex = RangeSex.FEMALE, low = d("1"), high = d("2"))))
        assertThat(Evaluator.number(bare, d("5"), man)).isEqualTo(Flag.NO_RANGE)
        val oneSided = row.copy(ranges = listOf(ReferenceRange(high = d("5.7"))))
        assertThat(Evaluator.number(oneSided, d("6.1"), man)).isEqualTo(Flag.HIGH)
        assertThat(Evaluator.number(oneSided, d("0"), man)).isEqualTo(Flag.NORMAL)
    }

    @Test
    fun `custom states match by label, choices and positive-negative flag abnormal answers`() {
        val custom = row.copy(ranges = listOf(ReferenceRange(low = d("1"), high = d("2")), ReferenceRange(state = PhysioState.CUSTOM, customState = "Nahorga", low = d("3"), high = d("4"))))
        val p = PatientContext(RangeSex.MALE, LocalDate.of(1980, 1, 1), today, PhysioState.CUSTOM, "nahorga")
        assertThat(custom.rangeFor(p)?.low).isEqualTo(d("3"))
        val colour = FormRow(UUID.randomUUID(), RowKind.CHOICE, "Rangi", options = listOf(ChoiceOption("Sariq", true), ChoiceOption("Qizil", false)))
        assertThat(Evaluator.choice(colour, "Sariq")).isEqualTo(Flag.NORMAL)
        assertThat(Evaluator.choice(colour, "Qizil")).isEqualTo(Flag.ABNORMAL)
        val hbs = FormRow(UUID.randomUUID(), RowKind.POS_NEG, "HBsAg")
        assertThat(Evaluator.posNeg(hbs, positive = false)).isEqualTo(Flag.NORMAL)
        assertThat(Evaluator.posNeg(hbs, positive = true)).isEqualTo(Flag.ABNORMAL)
    }

    @Test
    fun `validation points at the exact row and range`() {
        val bad = listOf(
            FormRow(UUID.randomUUID(), RowKind.NUMBER, " ", ranges = listOf(ReferenceRange(low = d("5"), high = d("1")), ReferenceRange())),
            FormRow(UUID.randomUUID(), RowKind.CHOICE, "Rangi", options = listOf(ChoiceOption("A", true))),
            FormRow(UUID.randomUUID(), RowKind.NUMBER, "Y", ranges = listOf(ReferenceRange(sex = RangeSex.MALE, state = PhysioState.PREGNANCY_T1, low = d("1")))),
        )
        assertThat(FormValidator.validate(bad).map { it.field to it.code }).containsExactlyInAnyOrder(
            "rows[0].name" to "validation.not_blank",
            "rows[0].ranges[0].high" to "lab.range_low_above_high",
            "rows[0].ranges[1]" to "lab.range_empty",
            "rows[1].options" to "lab.choice_options",
            "rows[2].ranges[0].state" to "lab.state_needs_female",
        )
        assertThat(FormValidator.validate(listOf(FormRow(UUID.randomUUID(), RowKind.HEADER, "Section")))).extracting("code").contains("lab.form_empty")
    }
}
