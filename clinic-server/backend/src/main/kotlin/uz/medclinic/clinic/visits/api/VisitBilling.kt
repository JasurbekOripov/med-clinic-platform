package uz.medclinic.clinic.visits.api

import java.time.Instant
import java.util.UUID

/** One unpaid service as the cashier sees it. */
data class BillableItem(
    val id: UUID,
    val serviceName: String,
    val doctorName: String?,
    val price: Long,
    val discount: Long,
    val amount: Long,
    /** The Admin let the patient be served before paying (debt). */
    val allowed: Boolean,
)

data class BillableVisit(val visitId: UUID, val visitNo: Long, val patientId: UUID, val createdAt: Instant, val items: List<BillableItem>) {
    val total: Long get() = items.sumOf { it.amount }
}

data class RefundedItem(val itemId: UUID, val visitId: UUID, val patientId: UUID, val serviceName: String, val paymentId: UUID, val amount: Long)

/** Used by the cashier module; the visit stays the single owner of each service's payment state. */
interface VisitBilling {
    /** Visits that still have unpaid services, newest first. */
    fun unpaid(offset: Long, limit: Int): Pair<List<BillableVisit>, Long>

    /**
     * Locks the visit until the transaction ends and returns exactly [itemIds], which must all be unpaid services of
     * that visit (`visit.item_not_payable` otherwise), so two cashiers can never take money for the same service.
     */
    fun lockForPayment(visitId: UUID, itemIds: Set<UUID>): BillableVisit

    /** Marks the locked items paid and publishes [VisitItemsPaid] in the same transaction. */
    fun markPaid(visitId: UUID, itemIds: Set<UUID>, paymentId: UUID, at: Instant)

    /** Admin's refund: the paid service becomes REFUNDED (`visit.item_not_refundable` otherwise). */
    fun markRefunded(itemId: UUID, at: Instant): RefundedItem

    /** Debts: services the Admin allowed before payment and still unpaid. */
    fun debts(): List<BillableVisit>
}

/** One ordered service as medical modules see it. */
data class VisitItemInfo(
    val itemId: UUID,
    val visitId: UUID,
    val patientId: UUID,
    val serviceName: String,
    val doctorId: UUID?,
    /** Paid, or allowed by the Admin before payment: medical staff may work on it. */
    val visibleToMedicalStaff: Boolean,
)

interface VisitQueries {
    fun item(itemId: UUID): VisitItemInfo?
}

/** A service that has just been paid, with what queues need to route it to a room. */
data class PaidItem(
    val id: UUID,
    val serviceId: UUID,
    val serviceName: String,
    val doctorId: UUID?,
    val doctorName: String?,
    val amount: Long,
)

/**
 * Published (synchronously, inside the payment transaction) when services become paid: they are now visible to
 * doctors and lab, and the queues module gives them queue numbers.
 */
data class VisitItemsPaid(val visitId: UUID, val patientId: UUID, val items: List<PaidItem>, val paymentId: UUID, val at: Instant)

/**
 * Published when the Admin lets unpaid services be served (emergency): medical modules (e.g. the lab work list) treat
 * them like paid ones; queues still number them only at payment.
 */
data class VisitItemsAllowed(val visitId: UUID, val patientId: UUID, val items: List<PaidItem>, val at: Instant)
