package uz.medclinic.clinic.queues.api

import java.time.LocalDate
import java.util.UUID

data class SlipLine(val serviceName: String, val doctorName: String?, val amount: Long)

/** What a room slip prints: the queue number in large type, the room and the services for that room. */
data class TicketSlip(
    val id: UUID,
    val label: String,
    val queueName: String,
    val room: String?,
    val day: LocalDate,
    val lines: List<SlipLine>,
)

interface TicketQueries {
    /** Slips created by one payment (one per room), in queue name order. */
    fun byPayment(paymentId: UUID): List<TicketSlip>
}
