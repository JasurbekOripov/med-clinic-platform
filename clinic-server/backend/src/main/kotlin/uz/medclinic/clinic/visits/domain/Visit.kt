package uz.medclinic.clinic.visits.domain

import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.domain.Text
import uz.medclinic.platform.domain.ValidationException
import java.time.Instant
import java.util.UUID

enum class ItemStatus { UNPAID, PAID, CANCELLED }

/** Discount chosen for the whole visit (one per visit), copied from the Admin's discount types. */
data class VisitDiscount(val typeId: UUID, val name: String, val percent: Int) {
    init {
        require(percent in 1..100)
    }

    /** Whole sum, rounded half up, exact integer arithmetic. */
    fun of(price: Long): Long = Math.addExact(Math.multiplyExact(price, percent.toLong()), 50) / 100
}

data class VisitAgent(val id: UUID, val name: String, val fee: Long)

data class Allowance(val by: UUID, val at: Instant, val reason: String)

data class Cancellation(val by: UUID?, val at: Instant, val reason: String)

data class VisitItem(
    val id: UUID,
    val serviceId: UUID,
    val serviceName: String,
    val groupName: String,
    val doctorId: UUID?,
    val doctorName: String?,
    val price: Long,
    val discount: Long,
    val status: ItemStatus,
    val paymentId: UUID?,
    val paidAt: Instant?,
    val allowance: Allowance?,
    val cancellation: Cancellation?,
    val createdBy: UUID?,
    val createdAt: Instant,
) {
    init {
        require(price >= 0 && discount in 0..price)
        require((status == ItemStatus.PAID) == (paymentId != null && paidAt != null))
        require((status == ItemStatus.CANCELLED) == (cancellation != null))
    }

    val amount: Long get() = price - discount

    /**
     * Doctors, lab and nurses see a service only when it is paid or the Admin allowed it (owner's rule:
     * unpaid patients are visible only to Reception and Cashier).
     */
    val visibleToMedicalStaff: Boolean get() = status == ItemStatus.PAID || (status == ItemStatus.UNPAID && allowance != null)
}

/** What the reception orders: the catalog prices it. */
data class OrderedService(
    val serviceId: UUID,
    val serviceName: String,
    val groupName: String,
    val doctorId: UUID?,
    val doctorName: String?,
    val price: Long,
)

data class Visit(
    val id: UUID,
    val visitNo: Long,
    val patientId: UUID,
    val discount: VisitDiscount?,
    val agent: VisitAgent?,
    val items: List<VisitItem>,
    val createdBy: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Int,
) {
    val activeItems: List<VisitItem> get() = items.filter { it.status != ItemStatus.CANCELLED }
    val total: Long get() = activeItems.sumOf { it.amount }
    val paid: Long get() = items.filter { it.status == ItemStatus.PAID }.sumOf { it.amount }
    val unpaid: Long get() = total - paid
    private val hasPaidItems: Boolean get() = items.any { it.status == ItemStatus.PAID }

    fun add(service: OrderedService, itemId: UUID, by: UUID?, now: Instant): Visit {
        if (items.size >= MAX_ITEMS) throw DomainException("visit.too_many_items", params = mapOf("max" to MAX_ITEMS))
        val item = VisitItem(
            itemId, service.serviceId, service.serviceName, service.groupName, service.doctorId, service.doctorName,
            service.price, discount?.of(service.price) ?: 0, ItemStatus.UNPAID, null, null, null, null, by, now,
        )
        return copy(items = items + item, updatedAt = now)
    }

    /** Reception may remove a service only while it is unpaid (owner's rule); after payment only the Admin refunds. */
    fun cancel(itemId: UUID, reason: String?, by: UUID?, now: Instant): Visit {
        val why = requiredReason(reason)
        val item = item(itemId)
        when (item.status) {
            ItemStatus.PAID -> throw DomainException("visit.item_already_paid")
            ItemStatus.CANCELLED -> throw DomainException("visit.item_already_cancelled")
            ItemStatus.UNPAID -> Unit
        }
        return replace(item.copy(status = ItemStatus.CANCELLED, cancellation = Cancellation(by, now, why)), now)
    }

    /** Changes the visit's discount; unpaid services are re-priced, paid ones keep what was paid. */
    fun withDiscount(newDiscount: VisitDiscount?, now: Instant): Visit = copy(
        discount = newDiscount,
        items = items.map { if (it.status == ItemStatus.UNPAID) it.copy(discount = newDiscount?.of(it.price) ?: 0) else it },
        updatedAt = now,
    )

    /** The agent earns a fee per paid visit, so the referral is fixed once anything is paid. */
    fun withAgent(newAgent: VisitAgent?, now: Instant): Visit {
        if (newAgent?.id == agent?.id) return this
        if (hasPaidItems) throw DomainException("visit.agent_locked")
        return copy(agent = newAgent, updatedAt = now)
    }

    /** Admin's emergency permission: the listed unpaid services become visible to medical staff before payment. */
    fun allow(itemIds: Set<UUID>, reason: String?, by: UUID, now: Instant): Visit {
        val why = requiredReason(reason)
        val targets = itemIds.map(::item)
        if (targets.isEmpty() || targets.any { it.status != ItemStatus.UNPAID || it.allowance != null }) {
            throw DomainException("visit.item_not_allowable")
        }
        return targets.fold(this) { v, it -> v.replace(it.copy(allowance = Allowance(by, now, why)), now) }
    }

    /** Called by the cashier with the visit locked: every item must still be unpaid. */
    fun requirePayable(itemIds: Set<UUID>): List<VisitItem> {
        val targets = itemIds.map { id -> items.firstOrNull { it.id == id } ?: throw DomainException("visit.item_not_payable") }
        if (targets.isEmpty() || targets.any { it.status != ItemStatus.UNPAID }) throw DomainException("visit.item_not_payable")
        return targets
    }

    fun markPaid(itemIds: Set<UUID>, paymentId: UUID, now: Instant): Visit =
        requirePayable(itemIds).fold(this) { v, it -> v.replace(it.copy(status = ItemStatus.PAID, paymentId = paymentId, paidAt = now), now) }

    private fun item(id: UUID): VisitItem = items.firstOrNull { it.id == id } ?: throw DomainException("visit.item_not_found", DomainException.Kind.NOT_FOUND)

    private fun replace(item: VisitItem, now: Instant) = copy(items = items.map { if (it.id == item.id) item else it }, updatedAt = now)

    companion object {
        const val MAX_ITEMS = 100

        fun requiredReason(reason: String?): String {
            val clean = Text.clean(reason) ?: throw ValidationException("reason", "validation.not_blank")
            if (clean.length > 500) throw ValidationException("reason", "validation.size", mapOf("max" to 500))
            return clean
        }
    }
}

interface VisitRepository {
    /** Assigns the visit number. */
    fun insert(visit: Visit): Visit
    fun findById(id: UUID): Visit?

    /** Same as [findById] but holds a row lock until the transaction ends. */
    fun lock(id: UUID): Visit?

    /** Saves the visit row (optimistic version check) and all its items. */
    fun update(visit: Visit): Visit
    fun findByPatient(patientId: UUID, limit: Int): List<Visit>
    fun findByItem(itemId: UUID): Visit?

    /** Visits with at least one unpaid service, newest first. */
    fun findUnpaid(offset: Long, limit: Int): Pair<List<Visit>, Long>
}
