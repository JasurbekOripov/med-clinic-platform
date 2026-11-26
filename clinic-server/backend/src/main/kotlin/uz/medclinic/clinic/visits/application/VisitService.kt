package uz.medclinic.clinic.visits.application

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.clinic.agents.api.AgentQueries
import uz.medclinic.clinic.audit.api.AuditEvent
import uz.medclinic.clinic.audit.api.AuditRecorder
import uz.medclinic.clinic.catalog.api.CatalogQueries
import uz.medclinic.clinic.patients.api.PatientQueries
import uz.medclinic.clinic.visits.api.BillableItem
import uz.medclinic.clinic.visits.api.BillableVisit
import uz.medclinic.clinic.visits.api.PaidItem
import uz.medclinic.clinic.visits.api.VisitBilling
import uz.medclinic.clinic.visits.api.VisitItemsPaid
import uz.medclinic.clinic.visits.domain.ItemStatus
import uz.medclinic.clinic.visits.domain.OrderedService
import uz.medclinic.clinic.visits.domain.Visit
import uz.medclinic.clinic.visits.domain.VisitAgent
import uz.medclinic.clinic.visits.domain.VisitDiscount
import uz.medclinic.clinic.visits.domain.VisitItem
import uz.medclinic.clinic.visits.domain.VisitRepository
import uz.medclinic.common.Ids
import uz.medclinic.platform.domain.NotFoundException
import uz.medclinic.platform.domain.ValidationException
import uz.medclinic.platform.domain.VersionConflictException
import uz.medclinic.platform.security.CurrentActor
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class ServiceOrder(val serviceId: UUID, val doctorId: UUID?)

@Service
class VisitService(
    private val visits: VisitRepository,
    private val patients: PatientQueries,
    private val catalog: CatalogQueries,
    private val agents: AgentQueries,
    private val audit: AuditRecorder,
    private val events: ApplicationEventPublisher,
    private val clock: Clock,
) : VisitBilling {
    @Transactional
    fun create(patientId: UUID, discountTypeId: UUID?, agentId: UUID?, orders: List<ServiceOrder>): Visit {
        patients.find(patientId) ?: throw ValidationException("patientId", "visit.unknown_patient")
        if (orders.isEmpty()) throw ValidationException("services", "validation.not_empty")
        if (orders.size > Visit.MAX_ITEMS) throw ValidationException("services", "validation.size", mapOf("max" to Visit.MAX_ITEMS))
        val now = clock.instant()
        val actor = CurrentActor.get().actorId
        val empty = Visit(Ids.newId(), 0, patientId, discountTypeId?.let(::discount), agentId?.let(::agent), emptyList(), actor, now, now, 0)
        val visit = orders.fold(empty) { v, order -> v.add(quote(order), Ids.newId(), actor, now) }
        val created = visits.insert(visit)
        audit.record(AuditEvent("visit.created", ENTITY, created.id, after = snapshot(created)))
        return created
    }

    @Transactional
    fun addService(id: UUID, version: Int, order: ServiceOrder): Visit {
        val current = load(id, version)
        val updated = visits.update(current.add(quote(order), Ids.newId(), CurrentActor.get().actorId, clock.instant()))
        audit.record(AuditEvent("visit.service_added", ENTITY, id, before = snapshot(current), after = snapshot(updated)))
        return updated
    }

    @Transactional
    fun cancelService(id: UUID, version: Int, itemId: UUID, reason: String?): Visit {
        val current = load(id, version)
        val updated = visits.update(current.cancel(itemId, reason, CurrentActor.get().actorId, clock.instant()))
        audit.record(AuditEvent("visit.service_cancelled", ENTITY, id, before = snapshot(current), after = snapshot(updated)))
        return updated
    }

    /** Discount and referral agent of the visit. */
    @Transactional
    fun update(id: UUID, version: Int, discountTypeId: UUID?, agentId: UUID?): Visit {
        val current = load(id, version)
        val now = clock.instant()
        val newDiscount = when (discountTypeId) {
            current.discount?.typeId -> current.discount // unchanged: keep the copy even if the type was edited since
            null -> null
            else -> discount(discountTypeId)
        }
        val newAgent = if (agentId == current.agent?.id) current.agent else agentId?.let(::agent)
        val changed = current.withAgent(newAgent, now).let { if (newDiscount != current.discount) it.withDiscount(newDiscount, now) else it }
        if (changed == current) return current
        val updated = visits.update(changed)
        audit.record(AuditEvent("visit.updated", ENTITY, id, before = snapshot(current), after = snapshot(updated)))
        return updated
    }

    /** Admin's emergency permission: serve before payment; the amount stays owed (a debt). */
    @Transactional
    fun allow(id: UUID, version: Int, itemIds: Set<UUID>, reason: String?): Visit {
        val current = load(id, version)
        val updated = visits.update(current.allow(itemIds, reason, CurrentActor.staffId(), clock.instant()))
        audit.record(AuditEvent("visit.services_allowed_unpaid", ENTITY, id, before = snapshot(current), after = snapshot(updated)))
        return updated
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): Visit = visits.findById(id) ?: throw NotFoundException(ENTITY, id)

    @Transactional(readOnly = true)
    fun byPatient(patientId: UUID): List<Visit> = visits.findByPatient(patientId, HISTORY_LIMIT)

    // ---- VisitBilling (cashier)

    @Transactional(readOnly = true)
    override fun unpaid(offset: Long, limit: Int): Pair<List<BillableVisit>, Long> {
        val (items, total) = visits.findUnpaid(offset, limit)
        return items.map { billable(it, it.items.filter { i -> i.status == ItemStatus.UNPAID }) } to total
    }

    @Transactional
    override fun lockForPayment(visitId: UUID, itemIds: Set<UUID>): BillableVisit {
        val visit = visits.lock(visitId) ?: throw NotFoundException(ENTITY, visitId)
        return billable(visit, visit.requirePayable(itemIds))
    }

    @Transactional
    override fun markPaid(visitId: UUID, itemIds: Set<UUID>, paymentId: UUID, at: Instant) {
        val current = visits.lock(visitId) ?: throw NotFoundException(ENTITY, visitId)
        val updated = visits.update(current.markPaid(itemIds, paymentId, at))
        audit.record(AuditEvent("visit.services_paid", ENTITY, visitId, before = snapshot(current), after = snapshot(updated)))
        val paid = updated.items.filter { it.id in itemIds }
            .map { PaidItem(it.id, it.serviceId, it.serviceName, it.doctorId, it.doctorName, it.amount) }
        events.publishEvent(VisitItemsPaid(visitId, updated.patientId, paid, paymentId, at))
    }

    // ---- helpers

    private fun billable(v: Visit, items: List<VisitItem>) = BillableVisit(
        v.id, v.visitNo, v.patientId, v.createdAt,
        items.map { BillableItem(it.id, it.serviceName, it.doctorName, it.price, it.discount, it.amount, it.allowance != null) },
    )

    private fun quote(order: ServiceOrder): OrderedService {
        val q = catalog.quote(order.serviceId, order.doctorId)
        return OrderedService(q.serviceId, q.serviceName, q.groupName, q.doctorId, q.doctorName, q.price)
    }

    private fun discount(id: UUID): VisitDiscount = catalog.discount(id).let { VisitDiscount(it.id, it.name, it.percent) }

    private fun agent(id: UUID): VisitAgent {
        val a = agents.find(id)?.takeIf { it.active } ?: throw ValidationException("agentId", "visit.unknown_agent")
        return VisitAgent(a.id, a.name, a.fee)
    }

    private fun load(id: UUID, version: Int): Visit {
        val visit = get(id)
        if (visit.version != version) throw VersionConflictException(ENTITY, id)
        return visit
    }

    /** Ids and amounts only: names of services are enough to read the log, no patient data. */
    private fun snapshot(v: Visit) = mapOf(
        "visitNo" to v.visitNo,
        "patientId" to v.patientId,
        "discount" to v.discount?.let { mapOf("name" to it.name, "percent" to it.percent) },
        "agent" to v.agent?.let { mapOf("id" to it.id, "fee" to it.fee) },
        "items" to v.items.map {
            mapOf(
                "id" to it.id, "service" to it.serviceName, "doctorId" to it.doctorId, "price" to it.price, "discount" to it.discount,
                "status" to it.status, "allowReason" to it.allowance?.reason, "cancelReason" to it.cancellation?.reason,
            )
        },
    )

    private companion object {
        const val ENTITY = "visit"
        const val HISTORY_LIMIT = 200
    }
}
