package uz.medclinic.clinic.lab.application

import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.clinic.audit.api.AuditEvent
import uz.medclinic.clinic.audit.api.AuditRecorder
import uz.medclinic.clinic.identity.api.StaffDirectory
import uz.medclinic.clinic.lab.domain.ChoiceOption
import uz.medclinic.clinic.lab.domain.Evaluator
import uz.medclinic.clinic.lab.domain.Flag
import uz.medclinic.clinic.lab.domain.FormRow
import uz.medclinic.clinic.lab.domain.LabResult
import uz.medclinic.clinic.lab.domain.LabResultRepository
import uz.medclinic.clinic.lab.domain.PatientContext
import uz.medclinic.clinic.lab.domain.PhysioState
import uz.medclinic.clinic.lab.domain.RangeKind
import uz.medclinic.clinic.lab.domain.RangeSex
import uz.medclinic.clinic.lab.domain.ResultEntry
import uz.medclinic.clinic.lab.domain.ResultStatus
import uz.medclinic.clinic.lab.domain.RowKind
import uz.medclinic.clinic.lab.domain.RowValue
import uz.medclinic.clinic.patients.api.PatientQueries
import uz.medclinic.clinic.patients.api.PatientSummary
import uz.medclinic.clinic.shared.CurrentPermissions
import uz.medclinic.clinic.shared.Permission
import uz.medclinic.clinic.visits.api.PaidItem
import uz.medclinic.clinic.visits.api.VisitItemsAllowed
import uz.medclinic.clinic.visits.api.VisitItemsPaid
import uz.medclinic.common.Ids
import uz.medclinic.common.TashkentTime
import uz.medclinic.platform.domain.NotFoundException
import uz.medclinic.platform.domain.VersionConflictException
import uz.medclinic.platform.security.CurrentActor
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** One row as shown and printed: the value, its flag and the norm chosen for this patient. */
data class RowView(
    val row: FormRow,
    val value: RowValue?,
    val flag: Flag?,
    val norm: String?,
    val normalLow: BigDecimal?,
    val normalHigh: BigDecimal?,
    val criticalLow: BigDecimal?,
    val criticalHigh: BigDecimal?,
)

data class ResultView(
    val result: LabResult,
    val patient: PatientSummary?,
    val templateName: String?,
    val rows: List<RowView>,
    /** The form has ranges for pregnancy / cycle phase / custom states: the lab picks the patient's state. */
    val usesStates: Boolean,
    val customStates: List<String>,
)

@Service
class LabResultService(
    private val results: LabResultRepository,
    private val templates: LabTemplateService,
    private val patients: PatientQueries,
    private val staff: StaffDirectory,
    private val audit: AuditRecorder,
    private val clock: Clock,
) {
    /** Paid (or Admin-allowed) services that have a lab form appear on the lab's work list, in the same transaction. */
    @EventListener
    fun onPaid(event: VisitItemsPaid) = release(event.visitId, event.patientId, event.items, event.at)

    @EventListener
    fun onAllowed(event: VisitItemsAllowed) = release(event.visitId, event.patientId, event.items, event.at)

    private fun release(visitId: UUID, patientId: UUID, items: List<PaidItem>, at: Instant) {
        items.forEach { item ->
            val form = templates.forService(item.serviceId) ?: return@forEach
            results.insertIfAbsent(
                LabResult(
                    Ids.newId(), item.id, visitId, patientId, item.serviceName, form.id, null, PhysioState.NONE, null, emptyMap(), null,
                    ResultStatus.WAITING, null, null, null, null, null, null, at, at, 0,
                ),
            )
        }
    }

    @Transactional(readOnly = true)
    fun workList(day: LocalDate?): List<Pair<LabResult, PatientSummary?>> {
        val d = day ?: TashkentTime.dateOf(clock.instant())
        val list = results.workList(TashkentTime.startOfDay(d), TashkentTime.endOfDayExclusive(d))
        val people = patients.findAll(list.map { it.patientId })
        return list.map { it to people[it.patientId] }
    }

    /** [previewState] lets the entry screen show the norms for a state before saving it. */
    @Transactional(readOnly = true)
    fun view(id: UUID, previewState: PhysioState? = null, previewCustom: String? = null): ResultView {
        val result = load(id)
        if (result.status != ResultStatus.READY && !CurrentPermissions.hasAny(Permission.LAB_WORK)) throw NotFoundException(ENTITY, id)
        return buildView(result, previewState, previewCustom)
    }

    /** Doctors, nurses and Reception see ready results only; the lab also sees work in progress. */
    @Transactional(readOnly = true)
    fun byPatient(patientId: UUID): List<ResultView> =
        results.findByPatient(patientId, readyOnly = !CurrentPermissions.hasAny(Permission.LAB_WORK)).map { buildView(it, null, null) }

    @Transactional
    fun save(id: UUID, version: Int, state: PhysioState?, customState: String?, values: Map<UUID, RowValue>, comment: String?): ResultView {
        val current = loadVersion(id, version)
        val (versionId, rows) = formOf(current)
        val entry = ResultEntry.of(rows, state, customState, values, comment)
        val updated = results.update(current.saveDraft(entry, versionId, CurrentActor.staffId(), clock.instant()))
        return buildView(updated, null, null)
    }

    @Transactional
    fun ready(id: UUID, version: Int): ResultView {
        val current = loadVersion(id, version)
        val me = CurrentActor.staffId()
        val updated = results.update(current.ready(me, staff.find(me)?.fullName ?: CurrentActor.get().actorName, clock.instant()))
        audit.record(AuditEvent("lab_result.ready", ENTITY, id, after = snapshot(updated)))
        return buildView(updated, null, null)
    }

    @Transactional
    fun correct(id: UUID, version: Int, state: PhysioState?, customState: String?, values: Map<UUID, RowValue>, comment: String?, reason: String?): ResultView {
        val current = loadVersion(id, version)
        val rows = templates.rowsOfVersion(current.templateVersionId ?: throw NotFoundException(ENTITY, id))
        val entry = ResultEntry.of(rows, state, customState, values, comment)
        val updated = results.update(current.correct(entry, reason, clock.instant()))
        audit.record(AuditEvent("lab_result.corrected", ENTITY, id, before = snapshot(current), after = snapshot(updated) + ("reason" to updated.correctionReason)))
        return buildView(updated, null, null)
    }

    @Transactional(readOnly = true)
    fun signature(id: UUID): Pair<ByteArray, String> {
        val result = load(id)
        val by = result.readyBy?.takeIf { result.status == ResultStatus.READY } ?: throw NotFoundException("signature", id)
        return staff.signature(by) ?: throw NotFoundException("signature", id)
    }

    /** The version entered with; for a result not saved yet, the form's current version. */
    private fun formOf(r: LabResult): Pair<UUID, List<FormRow>> =
        r.templateVersionId?.let { it to templates.rowsOfVersion(it) } ?: templates.get(r.templateId).let { it.versionId to it.rows }

    private fun buildView(r: LabResult, previewState: PhysioState?, previewCustom: String?): ResultView {
        val (_, rows) = formOf(r)
        val patient = patients.find(r.patientId)
        val templateName = runCatching { templates.get(r.templateId).name }.getOrNull()
        val day = r.readyAt?.let(TashkentTime::dateOf) ?: TashkentTime.dateOf(clock.instant())
        val context = patient?.let {
            PatientContext(if (it.gender == "FEMALE") RangeSex.FEMALE else RangeSex.MALE, it.birthDate, day, previewState ?: r.state, previewCustom ?: r.customState)
        }
        val views = rows.map { row ->
            val value = r.values[row.id]
            val normal = context?.let { row.rangeFor(it) }
            val critical = context?.let { row.rangeFor(it, RangeKind.CRITICAL) }
            val flag = if (value == null || context == null) null else when (row.kind) {
                RowKind.NUMBER -> value.number?.let { Evaluator.number(row, it, context) }
                RowKind.CHOICE -> value.choice?.let { Evaluator.choice(row, it) }
                RowKind.POS_NEG -> value.positive?.let { Evaluator.posNeg(row, it) }
                else -> null
            }
            val norm = when (row.kind) {
                RowKind.NUMBER -> normal?.display()
                RowKind.CHOICE -> row.options.filter(ChoiceOption::normal).joinToString(", ") { it.label }.ifEmpty { null }
                RowKind.POS_NEG -> null
                else -> null
            }
            RowView(row, value, flag, norm, normal?.low, normal?.high, critical?.low, critical?.high)
        }
        val states = rows.flatMap { it.ranges }.filter { it.state != PhysioState.NONE }
        return ResultView(r, patient, templateName, views, states.isNotEmpty(), states.mapNotNull { it.customState }.distinct())
    }

    private fun load(id: UUID): LabResult = results.findById(id) ?: throw NotFoundException(ENTITY, id)

    private fun loadVersion(id: UUID, version: Int): LabResult = load(id).also { if (it.version != version) throw VersionConflictException(ENTITY, id) }

    private fun snapshot(r: LabResult) = mapOf(
        "service" to r.serviceName, "status" to r.status, "state" to r.state, "values" to r.values.mapKeys { it.key.toString() }, "comment" to r.comment,
    )

    private companion object {
        const val ENTITY = "lab_result"
    }
}
