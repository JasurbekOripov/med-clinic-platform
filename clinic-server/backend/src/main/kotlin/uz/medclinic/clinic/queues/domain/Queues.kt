package uz.medclinic.clinic.queues.domain

import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.domain.FieldViolation
import uz.medclinic.platform.domain.Text
import uz.medclinic.platform.domain.ValidationException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class QueueDetails(
    val name: String,
    val prefix: String,
    val room: String?,
    val doctorId: UUID?,
    val serviceIds: Set<UUID>,
    val active: Boolean,
) {
    companion object {
        private val PREFIX = Regex("[A-Z]{1,2}")

        fun of(name: String?, prefix: String?, room: String?, doctorId: UUID?, serviceIds: Set<UUID>, active: Boolean): QueueDetails {
            val errors = mutableListOf<FieldViolation>()
            val cleanName = Text.clean(name)
            if (cleanName == null) errors += FieldViolation("name", "validation.not_blank")
            else if (cleanName.length > 100) errors += FieldViolation("name", "validation.size", mapOf("max" to 100))
            val cleanPrefix = Text.clean(prefix)?.uppercase()
            if (cleanPrefix == null) errors += FieldViolation("prefix", "validation.not_blank")
            else if (!PREFIX.matches(cleanPrefix)) errors += FieldViolation("prefix", "validation.queue_prefix")
            val cleanRoom = Text.clean(room)
            if (cleanRoom != null && cleanRoom.length > 50) errors += FieldViolation("room", "validation.size", mapOf("max" to 50))
            if (doctorId == null && serviceIds.isEmpty()) errors += FieldViolation("serviceIds", "queue.nothing_routed")
            if (errors.isNotEmpty()) throw ValidationException(errors)
            return QueueDetails(cleanName!!, cleanPrefix!!, cleanRoom, doctorId, serviceIds, active)
        }
    }
}

data class Queue(
    val id: UUID,
    val details: QueueDetails,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val version: Int,
) {
    fun withDetails(details: QueueDetails, now: Instant) = copy(details = details, updatedAt = now)

    fun deleted(now: Instant) = copy(deletedAt = now, updatedAt = now)
}

/** A just-paid service to route; mirrors `visits.api.PaidItem` so the domain does not depend on another module. */
data class Routable(val itemId: UUID, val serviceId: UUID, val doctorId: UUID?)

object QueueRouter {
    /**
     * The doctor's own queue wins (a consultation goes to the chosen doctor's room); otherwise the queue the service
     * is listed in. Inactive queues are ignored; services with no queue get no slip (e.g. a certificate).
     */
    fun route(items: List<Routable>, queues: List<Queue>): Map<UUID, List<Routable>> {
        val active = queues.filter { it.details.active && it.deletedAt == null }
        val byDoctor = active.filter { it.details.doctorId != null }.associateBy { it.details.doctorId }
        val byService = active.flatMap { q -> q.details.serviceIds.map { it to q } }.toMap()
        return items.mapNotNull { item ->
            val queue = item.doctorId?.let(byDoctor::get) ?: byService[item.serviceId]
            queue?.let { it.id to item }
        }.groupBy({ it.first }, { it.second })
    }
}

enum class TicketStatus { WAITING, DONE }

data class TicketItem(val visitItemId: UUID, val serviceName: String, val doctorName: String?, val amount: Long)

data class Ticket(
    val id: UUID,
    val queueId: UUID,
    val day: LocalDate,
    val number: Int,
    val visitId: UUID,
    val patientId: UUID,
    val paymentId: UUID,
    val items: List<TicketItem>,
    val status: TicketStatus,
    val createdAt: Instant,
    val doneAt: Instant?,
    val doneBy: UUID?,
    val version: Int,
) {
    fun markDone(by: UUID, now: Instant): Ticket {
        if (status == TicketStatus.DONE) throw DomainException("ticket.already_done")
        return copy(status = TicketStatus.DONE, doneAt = now, doneBy = by)
    }

    /** "Seen" pressed by mistake: put the patient back in the waiting list. */
    fun reopen(): Ticket {
        if (status == TicketStatus.WAITING) throw DomainException("ticket.not_done")
        return copy(status = TicketStatus.WAITING, doneAt = null, doneBy = null)
    }

    companion object {
        /** `A-007`: letter(s) of the queue and the day's number, at least three digits. */
        fun label(prefix: String, number: Int): String = "$prefix-${number.toString().padStart(3, '0')}"
    }
}

interface QueueRepository {
    fun insert(queue: Queue)
    fun findById(id: UUID): Queue?
    fun findAll(): List<Queue>
    fun update(queue: Queue): Queue
}

interface TicketRepository {
    /** Next number of [queueId] for [day], atomically (concurrent cashiers never get the same number). */
    fun nextNumber(queueId: UUID, day: LocalDate): Int
    fun insert(ticket: Ticket)
    fun findById(id: UUID): Ticket?
    fun findByQueue(queueId: UUID, day: LocalDate): List<Ticket>
    fun findByPayment(paymentId: UUID): List<Ticket>
    fun update(ticket: Ticket): Ticket
}
