package uz.medclinic.clinic.conclusions.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.clinic.audit.api.AuditEvent
import uz.medclinic.clinic.audit.api.AuditRecorder
import uz.medclinic.clinic.conclusions.domain.Conclusion
import uz.medclinic.clinic.conclusions.domain.ConclusionNote
import uz.medclinic.clinic.conclusions.domain.ConclusionRepository
import uz.medclinic.clinic.conclusions.domain.ConclusionTemplate
import uz.medclinic.clinic.conclusions.domain.ConclusionTemplateRepository
import uz.medclinic.clinic.conclusions.domain.ConclusionText
import uz.medclinic.clinic.identity.api.StaffDirectory
import uz.medclinic.clinic.visits.api.VisitQueries
import uz.medclinic.common.Ids
import uz.medclinic.common.TashkentTime
import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.domain.NotFoundException
import uz.medclinic.platform.domain.ValidationException
import uz.medclinic.platform.domain.VersionConflictException
import uz.medclinic.platform.security.CurrentActor
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

@Service
class ConclusionService(
    private val conclusions: ConclusionRepository,
    private val templates: ConclusionTemplateRepository,
    private val visits: VisitQueries,
    private val staff: StaffDirectory,
    private val audit: AuditRecorder,
    private val clock: Clock,
) {
    fun today(): LocalDate = TashkentTime.dateOf(clock.instant())

    fun writtenOn(c: Conclusion): LocalDate = TashkentTime.dateOf(c.createdAt)

    /**
     * Writes the conclusion of one ordered service. The service must be paid (or allowed by the Admin), and a service
     * ordered with a specific doctor can only be concluded by that doctor.
     */
    @Transactional
    fun write(visitItemId: UUID, text: ConclusionText): Conclusion {
        val item = visits.item(visitItemId)?.takeIf { it.visibleToMedicalStaff } ?: throw ValidationException("visitItemId", "conclusion.service_not_available")
        val me = CurrentActor.staffId()
        if (item.doctorId != null && item.doctorId != me) throw DomainException("conclusion.not_your_patient", DomainException.Kind.FORBIDDEN)
        val now = clock.instant()
        val conclusion = Conclusion(Ids.newId(), item.patientId, item.visitId, item.itemId, me, nameOf(me), text, emptyList(), now, now, 0)
        conclusions.insert(conclusion)
        audit.record(AuditEvent("conclusion.written", ENTITY, conclusion.id, after = snapshot(conclusion)))
        return conclusion
    }

    @Transactional
    fun edit(id: UUID, version: Int, text: ConclusionText): Conclusion {
        val current = get(id)
        if (current.version != version) throw VersionConflictException(ENTITY, id)
        val updated = conclusions.update(current.edited(CurrentActor.staffId(), text, today(), writtenOn(current), clock.instant()))
        audit.record(AuditEvent("conclusion.edited", ENTITY, id, before = snapshot(current), after = snapshot(updated)))
        return updated
    }

    /** Corrections after the day is over, signed and dated; the original text stays as it was printed. */
    @Transactional
    fun addNote(id: UUID, text: String?): Conclusion {
        val current = get(id)
        val me = CurrentActor.staffId()
        val note = ConclusionNote(Ids.newId(), me, nameOf(me), Conclusion.noteText(text), clock.instant())
        conclusions.addNote(id, note)
        audit.record(AuditEvent("conclusion.note_added", ENTITY, id, after = mapOf("note" to note.text)))
        return get(id)
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): Conclusion = conclusions.findById(id) ?: throw NotFoundException(ENTITY, id)

    @Transactional(readOnly = true)
    fun byPatient(patientId: UUID): List<Conclusion> = conclusions.findByPatient(patientId)

    @Transactional(readOnly = true)
    fun signature(id: UUID): Pair<ByteArray, String> = staff.signature(get(id).authorId) ?: throw NotFoundException("signature", id)

    // ---- the signed-in doctor's own templates

    @Transactional(readOnly = true)
    fun myTemplates(): List<ConclusionTemplate> = templates.findByOwner(CurrentActor.staffId())

    @Transactional
    fun saveTemplate(id: UUID?, version: Int?, name: String?, body: String?): ConclusionTemplate {
        val (cleanName, cleanBody) = ConclusionTemplate.validated(name, body)
        val now = clock.instant()
        if (id == null) {
            val template = ConclusionTemplate(Ids.newId(), CurrentActor.staffId(), cleanName, cleanBody, now, now, null, 0)
            templates.insert(template)
            return template
        }
        val current = ownTemplate(id, version ?: throw ValidationException("version", "validation.not_null"))
        return templates.update(current.copy(name = cleanName, body = cleanBody, updatedAt = now))
    }

    @Transactional
    fun deleteTemplate(id: UUID, version: Int) {
        val current = ownTemplate(id, version)
        val now = clock.instant()
        templates.update(current.copy(deletedAt = now, updatedAt = now))
    }

    /** Someone else's template is reported as not found: templates are private to their doctor. */
    private fun ownTemplate(id: UUID, version: Int): ConclusionTemplate {
        val t = templates.findById(id)?.takeIf { it.ownerId == CurrentActor.staffId() } ?: throw NotFoundException("conclusion_template", id)
        if (t.version != version) throw VersionConflictException("conclusion_template", id)
        return t
    }

    private fun nameOf(staffId: UUID): String = staff.find(staffId)?.fullName ?: CurrentActor.get().actorName

    private fun snapshot(c: Conclusion) = mapOf("visitItemId" to c.visitItemId, "title" to c.text.title, "body" to c.text.body)

    private companion object {
        const val ENTITY = "conclusion"
    }
}
