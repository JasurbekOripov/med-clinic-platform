package uz.medclinic.clinic.queues.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.clinic.audit.api.AuditEvent
import uz.medclinic.clinic.audit.api.AuditRecorder
import uz.medclinic.clinic.catalog.api.CatalogQueries
import uz.medclinic.clinic.identity.api.StaffDirectory
import uz.medclinic.clinic.queues.domain.Queue
import uz.medclinic.clinic.queues.domain.QueueDetails
import uz.medclinic.clinic.queues.domain.QueueRepository
import uz.medclinic.clinic.shared.Permission
import uz.medclinic.common.Ids
import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.domain.NotFoundException
import uz.medclinic.platform.domain.ValidationException
import uz.medclinic.platform.domain.VersionConflictException
import java.time.Clock
import java.util.UUID

/** Queue setup (Admin): letters, rooms, the doctor of a doctor's queue and which services go where. */
@Service
class QueueService(
    private val queues: QueueRepository,
    private val catalog: CatalogQueries,
    private val staff: StaffDirectory,
    private val audit: AuditRecorder,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun list(): List<Queue> = queues.findAll()

    @Transactional
    fun create(details: QueueDetails): Queue {
        validate(details, null)
        val now = clock.instant()
        val queue = Queue(Ids.newId(), details, now, now, null, 0)
        queues.insert(queue)
        audit.record(AuditEvent("queue.created", ENTITY, queue.id, after = snapshot(queue)))
        return queue
    }

    @Transactional
    fun update(id: UUID, version: Int, details: QueueDetails): Queue {
        val current = load(id, version)
        validate(details, current)
        val updated = queues.update(current.withDetails(details, clock.instant()))
        audit.record(AuditEvent("queue.updated", ENTITY, id, before = snapshot(current), after = snapshot(updated)))
        return updated
    }

    /** Soft delete; today's tickets keep their numbers and stay readable. */
    @Transactional
    fun delete(id: UUID, version: Int) {
        val current = load(id, version)
        queues.update(current.deleted(clock.instant()))
        audit.record(AuditEvent("queue.deleted", ENTITY, id, before = snapshot(current)))
    }

    /** Friendly, field-level answers for the clashes the unique indexes would otherwise reject. */
    private fun validate(d: QueueDetails, current: Queue?) {
        val others = queues.findAll().filter { it.id != current?.id }
        if (others.any { it.details.prefix == d.prefix }) throw ValidationException("prefix", "queue.prefix_taken")
        if (others.any { it.details.name.equals(d.name, ignoreCase = true) }) throw ValidationException("name", "queue.name_taken")
        if (d.doctorId != null) {
            if (others.any { it.details.doctorId == d.doctorId }) throw ValidationException("doctorId", "queue.doctor_has_queue")
            val keeps = d.doctorId == current?.details?.doctorId
            if (!keeps && staff.activeWith(Permission.CONSULTATION_WRITE).none { it.id == d.doctorId }) {
                throw ValidationException("doctorId", "catalog.unknown_doctor")
            }
        }
        val elsewhere = others.firstOrNull { it.details.serviceIds.any(d.serviceIds::contains) }
        if (elsewhere != null) throw DomainException("queue.service_in_other_queue", DomainException.Kind.CONFLICT, mapOf("queue" to elsewhere.details.name))
        // Services deleted from the price list since the form was opened are silently ignored by routing; new ones must exist.
        val added = d.serviceIds - current?.details?.serviceIds.orEmpty()
        if ((added - catalog.existingServices(added)).isNotEmpty()) throw ValidationException("serviceIds", "catalog.unknown_service")
    }

    private fun load(id: UUID, version: Int): Queue {
        val q = queues.findById(id) ?: throw NotFoundException(ENTITY, id)
        if (q.version != version) throw VersionConflictException(ENTITY, id)
        return q
    }

    private fun snapshot(q: Queue) = mapOf(
        "name" to q.details.name, "prefix" to q.details.prefix, "room" to q.details.room, "doctorId" to q.details.doctorId,
        "services" to q.details.serviceIds.map(UUID::toString).sorted(), "active" to q.details.active,
    )

    private companion object {
        const val ENTITY = "queue"
    }
}
