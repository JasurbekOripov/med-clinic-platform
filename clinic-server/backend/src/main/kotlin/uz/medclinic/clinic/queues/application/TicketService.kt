package uz.medclinic.clinic.queues.application

import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.clinic.audit.api.AuditEvent
import uz.medclinic.clinic.audit.api.AuditRecorder
import uz.medclinic.clinic.queues.api.SlipLine
import uz.medclinic.clinic.queues.api.TicketQueries
import uz.medclinic.clinic.queues.api.TicketSlip
import uz.medclinic.clinic.queues.domain.QueueRepository
import uz.medclinic.clinic.queues.domain.QueueRouter
import uz.medclinic.clinic.queues.domain.Routable
import uz.medclinic.clinic.queues.domain.Ticket
import uz.medclinic.clinic.queues.domain.TicketItem
import uz.medclinic.clinic.queues.domain.TicketRepository
import uz.medclinic.clinic.queues.domain.TicketStatus
import uz.medclinic.clinic.visits.api.VisitItemsPaid
import uz.medclinic.common.Ids
import uz.medclinic.common.TashkentTime
import uz.medclinic.platform.domain.NotFoundException
import uz.medclinic.platform.domain.VersionConflictException
import uz.medclinic.platform.security.CurrentActor
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

@Service
class TicketService(
    private val queues: QueueRepository,
    private val tickets: TicketRepository,
    private val audit: AuditRecorder,
    private val clock: Clock,
) : TicketQueries {
    /**
     * Runs inside the payment transaction: a payment and its queue numbers are saved together or not at all.
     * One ticket per room; the number belongs to the Tashkent day of the payment.
     */
    @EventListener
    fun onPaid(event: VisitItemsPaid) {
        val byId = event.items.associateBy { it.id }
        val routed = QueueRouter.route(event.items.map { Routable(it.id, it.serviceId, it.doctorId) }, queues.findAll())
        val day = TashkentTime.dateOf(event.at)
        routed.toSortedMap().forEach { (queueId, items) ->
            val ticket = Ticket(
                id = Ids.newId(), queueId = queueId, day = day, number = tickets.nextNumber(queueId, day),
                visitId = event.visitId, patientId = event.patientId, paymentId = event.paymentId,
                items = items.map { r -> byId.getValue(r.itemId).let { TicketItem(it.id, it.serviceName, it.doctorName, it.amount) } },
                status = TicketStatus.WAITING, createdAt = event.at, doneAt = null, doneBy = null, version = 0,
            )
            tickets.insert(ticket)
        }
    }

    @Transactional(readOnly = true)
    override fun byPayment(paymentId: UUID): List<TicketSlip> {
        val queuesById = queues.findAll().associateBy { it.id }
        return tickets.findByPayment(paymentId).mapNotNull { t ->
            val q = queuesById[t.queueId] ?: return@mapNotNull null
            TicketSlip(t.id, Ticket.label(q.details.prefix, t.number), q.details.name, q.details.room, t.day, t.items.map { SlipLine(it.serviceName, it.doctorName, it.amount) })
        }.sortedBy { it.queueName }
    }

    @Transactional(readOnly = true)
    fun today(queueId: UUID, day: LocalDate?): Pair<String, List<Ticket>> {
        val queue = queues.findById(queueId) ?: throw NotFoundException("queue", queueId)
        return queue.details.prefix to tickets.findByQueue(queueId, day ?: TashkentTime.dateOf(clock.instant()))
    }

    @Transactional
    fun setDone(id: UUID, version: Int, done: Boolean): Ticket {
        val current = tickets.findById(id) ?: throw NotFoundException("ticket", id)
        if (current.version != version) throw VersionConflictException("ticket", id)
        val changed = if (done) current.markDone(CurrentActor.staffId(), clock.instant()) else current.reopen()
        val updated = tickets.update(changed)
        audit.record(AuditEvent(if (done) "ticket.done" else "ticket.reopened", "ticket", id, after = mapOf("queueId" to current.queueId, "number" to current.number)))
        return updated
    }
}
