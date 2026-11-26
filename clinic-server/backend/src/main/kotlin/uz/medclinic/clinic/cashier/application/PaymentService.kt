package uz.medclinic.clinic.cashier.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.clinic.audit.api.AuditEvent
import uz.medclinic.clinic.audit.api.AuditRecorder
import uz.medclinic.clinic.cashier.domain.Payment
import uz.medclinic.clinic.cashier.domain.PaymentLine
import uz.medclinic.clinic.cashier.domain.PaymentMethod
import uz.medclinic.clinic.cashier.domain.PaymentRepository
import uz.medclinic.clinic.identity.api.StaffDirectory
import uz.medclinic.clinic.visits.api.BillableVisit
import uz.medclinic.clinic.visits.api.VisitBilling
import uz.medclinic.common.Ids
import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.domain.NotFoundException
import uz.medclinic.platform.domain.Page
import uz.medclinic.platform.domain.PageQuery
import uz.medclinic.platform.domain.ValidationException
import uz.medclinic.platform.security.CurrentActor
import java.time.Clock
import java.util.UUID

/** The amount on the cashier's screen no longer matches the services (someone changed the visit meanwhile). */
class AmountChangedException(expected: Long, actual: Long) :
    DomainException("payment.amount_changed", Kind.CONFLICT, mapOf("expected" to expected, "actual" to actual))

@Service
class PaymentService(
    private val payments: PaymentRepository,
    private val visits: VisitBilling,
    private val staff: StaffDirectory,
    private val audit: AuditRecorder,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun unpaid(page: PageQuery): Page<BillableVisit> {
        val (items, total) = visits.unpaid(page.offset, page.size)
        return Page(items, page.page, page.size, total)
    }

    /**
     * Takes money for the chosen unpaid services of one visit. [expectedAmount] is the total the cashier saw and
     * announced to the patient; if the visit changed meanwhile, nothing is charged (409 `payment.amount_changed`).
     */
    @Transactional
    fun pay(visitId: UUID, itemIds: Set<UUID>, method: PaymentMethod, expectedAmount: Long): Payment {
        if (itemIds.isEmpty()) throw ValidationException("itemIds", "validation.not_empty")
        val visit = visits.lockForPayment(visitId, itemIds)
        if (visit.total != expectedAmount) throw AmountChangedException(expectedAmount, visit.total)
        val cashierId = CurrentActor.staffId()
        val cashierName = staff.find(cashierId)?.fullName ?: CurrentActor.get().actorName
        val now = clock.instant()
        val payment = payments.insert(
            Payment(
                id = Ids.newId(), receiptNo = 0, visitId = visit.visitId, visitNo = visit.visitNo, patientId = visit.patientId,
                method = method, cashierId = cashierId, cashierName = cashierName,
                lines = visit.items.map { PaymentLine(it.id, it.serviceName, it.doctorName, it.price, it.discount, it.amount) },
                createdAt = now,
            ),
        )
        visits.markPaid(visitId, itemIds, payment.id, now)
        audit.record(
            AuditEvent(
                "payment.received", ENTITY, payment.id,
                after = mapOf("receiptNo" to payment.receiptNo, "visitId" to visitId, "amount" to payment.amount, "method" to method, "items" to itemIds.size),
            ),
        )
        return payment
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): Payment = payments.findById(id) ?: throw NotFoundException(ENTITY, id)

    @Transactional(readOnly = true)
    fun byVisit(visitId: UUID): List<Payment> = payments.findByVisit(visitId)

    private companion object {
        const val ENTITY = "payment"
    }
}
