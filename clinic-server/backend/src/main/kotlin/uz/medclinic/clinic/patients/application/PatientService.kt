package uz.medclinic.clinic.patients.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.clinic.audit.api.AuditEvent
import uz.medclinic.clinic.audit.api.AuditRecorder
import uz.medclinic.clinic.patients.api.PatientQueries
import uz.medclinic.clinic.patients.api.PatientSummary
import uz.medclinic.clinic.patients.domain.DuplicateCandidate
import uz.medclinic.clinic.patients.domain.Patient
import uz.medclinic.clinic.patients.domain.PatientDetails
import uz.medclinic.clinic.patients.domain.PatientRepository
import uz.medclinic.clinic.patients.domain.PatientSearch
import uz.medclinic.common.Ids
import uz.medclinic.common.TashkentTime
import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.domain.NotFoundException
import uz.medclinic.platform.domain.Page
import uz.medclinic.platform.domain.PageQuery
import uz.medclinic.platform.domain.VersionConflictException
import uz.medclinic.platform.security.CurrentActor
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/** Raised when a new patient looks like someone already registered; the UI offers to open the existing card. */
class PossibleDuplicateException(val candidates: List<DuplicateCandidate>) :
    DomainException("patient.possible_duplicate", Kind.CONFLICT)

@Service
class PatientService(
    private val patients: PatientRepository,
    private val audit: AuditRecorder,
    private val clock: Clock,
) : PatientQueries {
    fun today(): LocalDate = TashkentTime.dateOf(clock.instant())

    @Transactional(readOnly = true)
    fun search(text: String?, page: PageQuery): Page<Patient> {
        val (items, total) = patients.search(PatientSearch.parse(text), page.offset, page.size)
        return Page(items, page.page, page.size, total)
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): Patient = patients.findById(id) ?: throw NotFoundException(ENTITY, id)

    @Transactional(readOnly = true)
    fun duplicates(details: PatientDetails, excludeId: UUID? = null): List<DuplicateCandidate> = patients.duplicates(details, excludeId)

    /**
     * Creates a patient. Unless the receptionist confirmed it is a different person, possible duplicates block the
     * creation (409 `patient.possible_duplicate`) so the existing card can be opened instead.
     */
    @Transactional
    fun create(details: PatientDetails, confirmedNotDuplicate: Boolean): Patient {
        if (!confirmedNotDuplicate) {
            val candidates = patients.duplicates(details, null)
            if (candidates.isNotEmpty()) throw PossibleDuplicateException(candidates)
        }
        val now = clock.instant()
        val actor = CurrentActor.get().actorId
        val created = patients.insert(Patient(Ids.newId(), 0, details, actor, now, now, null, 0))
        audit.record(AuditEvent("patient.created", ENTITY, created.id, after = snapshot(created) + ("duplicateConfirmed" to confirmedNotDuplicate)))
        return created
    }

    @Transactional
    fun update(id: UUID, version: Int, details: PatientDetails): Patient {
        val current = load(id, version)
        val updated = patients.update(current.withDetails(details, clock.instant()))
        audit.record(AuditEvent("patient.updated", ENTITY, id, before = snapshot(current), after = snapshot(updated)))
        return updated
    }

    /** Soft delete: the record stays for history and audit but disappears from search. */
    @Transactional
    fun delete(id: UUID, version: Int) {
        val current = load(id, version)
        patients.update(current.deleted(clock.instant()))
        audit.record(AuditEvent("patient.deleted", ENTITY, id, before = snapshot(current)))
    }

    @Transactional(readOnly = true)
    override fun find(id: UUID): PatientSummary? = patients.findById(id)?.let(::summary)

    @Transactional(readOnly = true)
    override fun findAll(ids: Collection<UUID>): Map<UUID, PatientSummary> = patients.findAllById(ids.toSet()).associate { it.id to summary(it) }

    private fun summary(p: Patient) = PatientSummary(p.id, p.cardNo, p.details.fullName, p.details.gender.name, p.details.birthDate, p.details.phone.e164)

    private fun load(id: UUID, version: Int): Patient = get(id).also { if (it.version != version) throw VersionConflictException(ENTITY, id) }

    private fun snapshot(p: Patient) = mapOf(
        "cardNo" to p.cardNo, "lastName" to p.details.lastName, "firstName" to p.details.firstName, "patronymic" to p.details.patronymic,
        "gender" to p.details.gender, "birthDate" to p.details.birthDate, "phone" to p.details.phone.e164,
        "address" to p.details.address, "notes" to p.details.notes,
    )

    private companion object {
        const val ENTITY = "patient"
    }
}
