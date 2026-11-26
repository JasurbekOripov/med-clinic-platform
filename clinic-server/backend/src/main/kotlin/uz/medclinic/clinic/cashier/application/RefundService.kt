package uz.medclinic.clinic.cashier.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.clinic.audit.api.AuditEvent
import uz.medclinic.clinic.audit.api.AuditRecorder
import uz.medclinic.clinic.cashier.domain.PaymentRepository
import uz.medclinic.clinic.cashier.domain.Refund
import uz.medclinic.clinic.cashier.domain.RefundRepository
import uz.medclinic.clinic.cashier.domain.RefundStatus
import uz.medclinic.clinic.cashier.domain.ShiftRepository
import uz.medclinic.clinic.identity.api.StaffDirectory
import uz.medclinic.clinic.visits.api.BillableVisit
import uz.medclinic.clinic.visits.api.VisitBilling
import uz.medclinic.common.Ids
import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.domain.NotFoundException
import uz.medclinic.platform.domain.ValidationException
import uz.medclinic.platform.domain.VersionConflictException
import uz.medclinic.platform.security.CurrentActor
import java.time.Clock
import java.util.UUID

/**
 * Owner's rules: only the Admin refunds (whole service, reason required); the money is always given back in cash from
 * a cashier's open shift, so it is subtracted from that shift's expected cash.
 */
@Service
class RefundService(
    private val refunds: RefundRepository,
    private val payments: PaymentRepository,
    private val shifts: ShiftRepository,
    private val visits: VisitBilling,
    private val staff: StaffDirectory,
    private val audit: AuditRecorder,
    private val clock: Clock,
) {
    @Transactional
    fun approve(visitItemId: UUID?, reason: String?): Refund {
        val itemId = visitItemId ?: throw ValidationException("visitItemId", "validation.not_null")
        val why = Refund.reason(reason)
        val (payment, line) = payments.findLineByItem(itemId) ?: throw DomainException("visit.item_not_refundable")
        val now = clock.instant()
        val item = visits.markRefunded(itemId, now)
        val me = CurrentActor.staffId()
        val refund = Refund(
            Ids.newId(), payment.id, item.visitId, itemId, item.patientId, line.serviceName, line.amount, payment.method, why,
            me, staff.find(me)?.fullName ?: CurrentActor.get().actorName, now, RefundStatus.APPROVED, null, null, null, 0,
        )
        refunds.insert(refund)
        audit.record(AuditEvent("refund.approved", ENTITY, refund.id, after = mapOf("service" to line.serviceName, "amount" to line.amount, "reason" to why, "receiptNo" to payment.receiptNo)))
        return refund
    }

    @Transactional(readOnly = true)
    fun pending(): List<Refund> = refunds.pending()

    /** The cashier gives the money back from their open shift. */
    @Transactional
    fun payOut(id: UUID, version: Int): Refund {
        val refund = refunds.findById(id) ?: throw NotFoundException(ENTITY, id)
        if (refund.version != version) throw VersionConflictException(ENTITY, id)
        val me = CurrentActor.staffId()
        val shift = shifts.findOpen(me)?.let { shifts.lock(it.id) } ?: throw DomainException("shift.not_open", DomainException.Kind.CONFLICT)
        val paid = refunds.update(refund.paidOut(shift, me, clock.instant()))
        audit.record(AuditEvent("refund.paid_out", ENTITY, id, after = mapOf("shiftNo" to shift.shiftNo, "amount" to refund.amount)))
        return paid
    }

    @Transactional(readOnly = true)
    fun debts(): List<BillableVisit> = visits.debts()

    private companion object {
        const val ENTITY = "refund"
    }
}
