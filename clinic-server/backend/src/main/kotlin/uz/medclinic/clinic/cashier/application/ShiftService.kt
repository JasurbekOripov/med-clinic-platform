package uz.medclinic.clinic.cashier.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.clinic.audit.api.AuditEvent
import uz.medclinic.clinic.audit.api.AuditRecorder
import uz.medclinic.clinic.cashier.domain.Expense
import uz.medclinic.clinic.cashier.domain.ExpenseRepository
import uz.medclinic.clinic.cashier.domain.Refund
import uz.medclinic.clinic.cashier.domain.RefundRepository
import uz.medclinic.clinic.cashier.domain.Shift
import uz.medclinic.clinic.cashier.domain.ShiftRepository
import uz.medclinic.clinic.cashier.domain.ShiftStatus
import uz.medclinic.clinic.cashier.domain.ShiftTotals
import uz.medclinic.clinic.cashier.domain.cashAmount
import uz.medclinic.clinic.identity.api.StaffDirectory
import uz.medclinic.clinic.identity.api.StaffRef
import uz.medclinic.common.Ids
import uz.medclinic.common.TashkentTime
import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.domain.NotFoundException
import uz.medclinic.platform.domain.ValidationException
import uz.medclinic.platform.domain.VersionConflictException
import uz.medclinic.platform.security.CurrentActor
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/** X report while open, Z report when closed: same content. */
data class ShiftReport(val shift: Shift, val totals: ShiftTotals, val expenses: List<Expense>, val refunds: List<Refund>) {
    val expectedCash: Long get() = shift.expectedCash ?: totals.expectedCash(shift.openingCash)
}

@Service
class ShiftService(
    private val shifts: ShiftRepository,
    private val expenses: ExpenseRepository,
    private val refunds: RefundRepository,
    private val staff: StaffDirectory,
    private val audit: AuditRecorder,
    private val clock: Clock,
) {
    @Transactional
    fun open(openingCash: Long?): ShiftReport {
        val me = CurrentActor.staffId()
        val opening = if (openingCash == null) 0 else cashAmount("openingCash", openingCash, allowZero = true)
        val shift = shifts.insert(Shift(Ids.newId(), 0, me, nameOf(me), opening, clock.instant(), ShiftStatus.OPEN, null, null, null, null, 0))
        audit.record(AuditEvent("shift.opened", ENTITY, shift.id, after = mapOf("shiftNo" to shift.shiftNo, "openingCash" to opening)))
        return report(shift)
    }

    /** The signed-in cashier's open shift with live totals (X report), or null. */
    @Transactional(readOnly = true)
    fun current(): ShiftReport? = shifts.findOpen(CurrentActor.staffId())?.let(::report)

    @Transactional
    fun addExpense(recipientStaffId: UUID?, recipientName: String?, amount: Long?, comment: String?): ShiftReport {
        val shift = myOpenShift()
        val name = recipientStaffId?.let { id -> staff.find(id)?.fullName ?: throw ValidationException("recipientStaffId", "validation.invalid") } ?: recipientName
        val (cleanName, sum, text) = Expense.validated(name, amount, comment)
        val expense = Expense(Ids.newId(), shift.id, recipientStaffId, cleanName, sum, text, CurrentActor.staffId(), clock.instant())
        expenses.insert(expense)
        audit.record(
            AuditEvent("shift.expense", ENTITY, shift.id, after = mapOf("expenseId" to expense.id, "recipient" to cleanName, "amount" to sum, "comment" to text)),
        )
        return report(shift)
    }

    /** Z report: compares the counted cash with the expected amount and locks the shift. */
    @Transactional
    fun close(id: UUID, version: Int, countedCash: Long?, note: String?): ShiftReport {
        val shift = shifts.lock(id) ?: throw NotFoundException(ENTITY, id)
        if (shift.cashierId != CurrentActor.staffId()) throw DomainException("shift.not_yours", DomainException.Kind.FORBIDDEN)
        if (shift.version != version) throw VersionConflictException(ENTITY, id)
        val expected = shifts.totals(id).expectedCash(shift.openingCash)
        val closed = shifts.update(shift.close(expected, countedCash, note, clock.instant()))
        audit.record(
            AuditEvent(
                "shift.closed", ENTITY, id,
                after = mapOf("shiftNo" to closed.shiftNo, "expected" to expected, "counted" to closed.countedCash, "difference" to closed.difference),
            ),
        )
        return report(closed)
    }

    /** Reports are the cashier's own; managers with REPORTS_VIEW see every cashier's. */
    @Transactional(readOnly = true)
    fun get(id: UUID, canSeeAll: Boolean): ShiftReport {
        val shift = shifts.findById(id)?.takeIf { canSeeAll || it.cashierId == CurrentActor.staffId() } ?: throw NotFoundException(ENTITY, id)
        return report(shift)
    }

    @Transactional(readOnly = true)
    fun list(from: LocalDate, to: LocalDate): List<Pair<Shift, ShiftTotals>> =
        shifts.list(TashkentTime.startOfDay(from), TashkentTime.endOfDayExclusive(to)).map { it to shifts.totals(it.id) }

    @Transactional(readOnly = true)
    fun recipients(): List<StaffRef> = staff.active()

    private fun myOpenShift(): Shift = shifts.findOpen(CurrentActor.staffId())?.let { shifts.lock(it.id) }?.takeIf { it.status == ShiftStatus.OPEN }
        ?: throw DomainException("shift.not_open", DomainException.Kind.CONFLICT)

    private fun report(s: Shift) = ShiftReport(s, shifts.totals(s.id), expenses.byShift(s.id), refunds.byShift(s.id))

    private fun nameOf(id: UUID) = staff.find(id)?.fullName ?: CurrentActor.get().actorName

    private companion object {
        const val ENTITY = "cash_shift"
    }
}
