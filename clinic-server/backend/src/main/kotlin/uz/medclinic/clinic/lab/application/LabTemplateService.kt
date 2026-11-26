package uz.medclinic.clinic.lab.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.clinic.audit.api.AuditEvent
import uz.medclinic.clinic.audit.api.AuditRecorder
import uz.medclinic.clinic.catalog.api.CatalogQueries
import uz.medclinic.clinic.lab.domain.FormRow
import uz.medclinic.clinic.lab.domain.LabTemplate
import uz.medclinic.clinic.lab.domain.LabTemplateRepository
import uz.medclinic.common.Ids
import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.domain.NotFoundException
import uz.medclinic.platform.domain.ValidationException
import uz.medclinic.platform.domain.VersionConflictException
import uz.medclinic.platform.security.CurrentActor
import java.time.Clock
import java.util.UUID

@Service
class LabTemplateService(
    private val templates: LabTemplateRepository,
    private val catalog: CatalogQueries,
    private val audit: AuditRecorder,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun list(): List<LabTemplate> = templates.findAll()

    @Transactional(readOnly = true)
    fun get(id: UUID): LabTemplate = templates.findById(id) ?: throw NotFoundException(ENTITY, id)

    @Transactional
    fun create(name: String?, serviceIds: Set<UUID>, rows: List<FormRow>, active: Boolean): LabTemplate {
        val (cleanName, cleanRows) = LabTemplate.validated(name, rows)
        checkServices(serviceIds, null)
        val now = clock.instant()
        val template = LabTemplate(Ids.newId(), cleanName, serviceIds, cleanRows, 1, Ids.newId(), active, now, now, null, 0)
        templates.insert(template)
        audit.record(AuditEvent("lab_template.created", ENTITY, template.id, after = snapshot(template)))
        return template
    }

    /** A change of the rows becomes a new version; results already entered keep theirs. */
    @Transactional
    fun update(id: UUID, version: Int, name: String?, serviceIds: Set<UUID>, rows: List<FormRow>, active: Boolean): LabTemplate {
        val current = load(id, version)
        val (cleanName, cleanRows) = LabTemplate.validated(name, rows)
        checkServices(serviceIds, current)
        val changedRows = cleanRows != current.rows
        val updated = templates.update(
            current.copy(name = cleanName, serviceIds = serviceIds, rows = cleanRows, active = active, updatedAt = clock.instant()),
            changedRows, CurrentActor.get().actorId,
        )
        audit.record(AuditEvent("lab_template.updated", ENTITY, id, before = snapshot(current), after = snapshot(updated)))
        return updated
    }

    @Transactional
    fun delete(id: UUID, version: Int) {
        val current = load(id, version)
        val now = clock.instant()
        templates.update(current.copy(deletedAt = now, updatedAt = now), false, CurrentActor.get().actorId)
        audit.record(AuditEvent("lab_template.deleted", ENTITY, id, before = snapshot(current)))
    }

    /** Which form a paid service is filled in with (null: the service has no lab form). */
    @Transactional(readOnly = true)
    fun forService(serviceId: UUID): LabTemplate? = templates.findAll().firstOrNull { it.active && serviceId in it.serviceIds }

    @Transactional(readOnly = true)
    fun rowsOfVersion(versionId: UUID): List<FormRow> = templates.rowsOfVersion(versionId) ?: throw NotFoundException("lab_template_version", versionId)

    private fun checkServices(serviceIds: Set<UUID>, current: LabTemplate?) {
        if (serviceIds.size > 50) throw ValidationException("serviceIds", "validation.size", mapOf("max" to 50))
        val added = serviceIds - current?.serviceIds.orEmpty()
        if ((added - catalog.existingServices(added)).isNotEmpty()) throw ValidationException("serviceIds", "catalog.unknown_service")
        val taken = templates.findAll().firstOrNull { it.id != current?.id && it.serviceIds.any(serviceIds::contains) }
        if (taken != null) throw DomainException("lab.service_has_form", DomainException.Kind.CONFLICT, mapOf("form" to taken.name))
    }

    private fun load(id: UUID, version: Int): LabTemplate {
        val t = get(id)
        if (t.version != version) throw VersionConflictException(ENTITY, id)
        return t
    }

    private fun snapshot(t: LabTemplate) = mapOf(
        "name" to t.name, "versionNo" to t.versionNo, "active" to t.active, "services" to t.serviceIds.map(UUID::toString).sorted(), "rows" to t.rows.size,
    )

    private companion object {
        const val ENTITY = "lab_template"
    }
}
