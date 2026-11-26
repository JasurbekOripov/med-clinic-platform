package uz.medclinic.clinic.lab.domain

import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.domain.FieldViolation
import uz.medclinic.platform.domain.Text
import uz.medclinic.platform.domain.ValidationException
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class ResultStatus { WAITING, DRAFT, READY }

/** One entered value; which field is used depends on the row kind. */
data class RowValue(val number: BigDecimal? = null, val choice: String? = null, val positive: Boolean? = null, val text: String? = null)

data class LabResult(
    val id: UUID,
    val visitItemId: UUID,
    val visitId: UUID,
    val patientId: UUID,
    val serviceName: String,
    val templateId: UUID,
    /** Fixed when values are first saved; until then the form's current version is shown. */
    val templateVersionId: UUID?,
    val state: PhysioState,
    val customState: String?,
    val values: Map<UUID, RowValue>,
    val comment: String?,
    val status: ResultStatus,
    val enteredBy: UUID?,
    val readyBy: UUID?,
    val readyByName: String?,
    val readyAt: Instant?,
    val correctedAt: Instant?,
    val correctionReason: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Int,
) {
    fun saveDraft(entry: ResultEntry, versionId: UUID, by: UUID, now: Instant): LabResult {
        if (status == ResultStatus.READY) throw DomainException("lab.result_ready")
        return withEntry(entry, versionId).copy(status = ResultStatus.DRAFT, enteredBy = by, updatedAt = now)
    }

    fun ready(by: UUID, name: String, now: Instant): LabResult {
        when (status) {
            ResultStatus.READY -> throw DomainException("lab.result_ready")
            ResultStatus.WAITING -> throw DomainException("lab.result_empty")
            ResultStatus.DRAFT -> if (values.isEmpty()) throw DomainException("lab.result_empty")
        }
        return copy(status = ResultStatus.READY, readyBy = by, readyByName = name, readyAt = now, updatedAt = now)
    }

    /** After "ready" the result may already be printed: a correction needs a reason and is marked on the printout. */
    fun correct(entry: ResultEntry, reason: String?, now: Instant): LabResult {
        if (status != ResultStatus.READY) throw DomainException("lab.result_not_ready")
        val why = Text.clean(reason) ?: throw ValidationException("reason", "validation.not_blank")
        if (why.length > 500) throw ValidationException("reason", "validation.size", mapOf("max" to 500))
        if (entry.values.isEmpty()) throw DomainException("lab.result_empty")
        return withEntry(entry, templateVersionId!!).copy(correctedAt = now, correctionReason = why, updatedAt = now)
    }

    private fun withEntry(e: ResultEntry, versionId: UUID) =
        copy(templateVersionId = versionId, state = e.state, customState = e.customState, values = e.values, comment = e.comment)
}

/** What the lab worker enters, validated against the form's rows. */
data class ResultEntry(val state: PhysioState, val customState: String?, val values: Map<UUID, RowValue>, val comment: String?) {
    companion object {
        private val MAX_ABS = BigDecimal("1000000000")

        fun of(rows: List<FormRow>, state: PhysioState?, customState: String?, raw: Map<UUID, RowValue>, comment: String?): ResultEntry {
            val errors = mutableListOf<FieldViolation>()
            val byId = rows.associateBy { it.id }
            val cleanState = state ?: PhysioState.NONE
            val cleanCustom = Text.clean(customState)
            if (cleanState == PhysioState.CUSTOM && cleanCustom == null) errors += FieldViolation("customState", "validation.not_blank")
            val values = mutableMapOf<UUID, RowValue>()
            raw.forEach { (rowId, v) ->
                val row = byId[rowId]
                val at = "values.$rowId"
                if (row == null || row.kind == RowKind.HEADER) {
                    errors += FieldViolation(at, "lab.unknown_row")
                    return@forEach
                }
                when (row.kind) {
                    RowKind.NUMBER -> v.number?.let { n ->
                        if (n.abs() >= MAX_ABS || n.scale() > 6) errors += FieldViolation(at, "lab.value_invalid") else values[rowId] = RowValue(number = n)
                    }
                    RowKind.CHOICE -> Text.clean(v.choice)?.let { c ->
                        if (row.options.none { it.label == c }) errors += FieldViolation(at, "lab.value_invalid") else values[rowId] = RowValue(choice = c)
                    }
                    RowKind.POS_NEG -> v.positive?.let { values[rowId] = RowValue(positive = it) }
                    RowKind.TEXT -> Text.cleanMultiline(v.text)?.let { t ->
                        if (t.length > 2000) errors += FieldViolation(at, "validation.size", mapOf("max" to 2000)) else values[rowId] = RowValue(text = t)
                    }
                    RowKind.HEADER -> Unit
                }
            }
            val cleanComment = Text.cleanMultiline(comment)
            if (cleanComment != null && cleanComment.length > 2000) errors += FieldViolation("comment", "validation.size", mapOf("max" to 2000))
            if (errors.isNotEmpty()) throw ValidationException(errors)
            return ResultEntry(cleanState, if (cleanState == PhysioState.CUSTOM) cleanCustom else null, values, cleanComment)
        }
    }
}

interface LabResultRepository {
    /** False when a result for this service already exists (events may repeat: allowed, then paid). */
    fun insertIfAbsent(result: LabResult): Boolean
    fun findById(id: UUID): LabResult?
    fun update(result: LabResult): LabResult

    /** Not yet ready (any day) plus everything created on [day]. */
    fun workList(dayStart: Instant, dayEnd: Instant): List<LabResult>
    fun findByPatient(patientId: UUID, readyOnly: Boolean): List<LabResult>
}
