package uz.medclinic.clinic.cashier.web

import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uz.medclinic.clinic.cashier.application.RefundService
import uz.medclinic.clinic.cashier.application.ShiftReport
import uz.medclinic.clinic.cashier.application.ShiftService
import uz.medclinic.clinic.cashier.domain.PaymentMethod
import uz.medclinic.clinic.cashier.domain.Refund
import uz.medclinic.clinic.cashier.domain.RefundStatus
import uz.medclinic.clinic.cashier.domain.ShiftStatus
import uz.medclinic.clinic.cashier.domain.ShiftTotals
import uz.medclinic.clinic.identity.api.StaffRef
import uz.medclinic.clinic.patients.api.PatientQueries
import uz.medclinic.clinic.shared.CurrentPermissions
import uz.medclinic.clinic.shared.Permission
import uz.medclinic.clinic.shared.Permissions
import uz.medclinic.clinic.visits.api.BillableItem
import uz.medclinic.platform.domain.ValidationException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class ExpenseResponse(val id: UUID, val recipientName: String, val amount: Long, val comment: String, val createdAt: Instant)

data class RefundResponse(
    val id: UUID,
    val serviceName: String,
    val patient: PatientRef?,
    val amount: Long,
    val originalMethod: PaymentMethod,
    val reason: String,
    val approvedByName: String,
    val approvedAt: Instant,
    val status: RefundStatus,
    val paidOutAt: Instant?,
    val version: Int,
)

data class ShiftResponse(
    val id: UUID,
    val shiftNo: Long,
    val cashierName: String,
    val status: ShiftStatus,
    val openingCash: Long,
    val openedAt: Instant,
    val closedAt: Instant?,
    val totals: ShiftTotals,
    val expectedCash: Long,
    val countedCash: Long?,
    val difference: Long?,
    val closeNote: String?,
    val expenses: List<ExpenseResponse>,
    val refunds: List<RefundResponse>,
    val version: Int,
)

data class ShiftSummary(
    val id: UUID,
    val shiftNo: Long,
    val cashierName: String,
    val status: ShiftStatus,
    val openedAt: Instant,
    val closedAt: Instant?,
    val totals: ShiftTotals,
    val expectedCash: Long,
    val difference: Long?,
)

data class OpenShiftRequest(val openingCash: Long? = null)

data class CloseShiftRequest(val countedCash: Long?, @field:Size(max = 500) val note: String? = null, val version: Int?)

data class ExpenseRequest(
    val recipientStaffId: UUID? = null,
    @field:Size(max = 200) val recipientName: String? = null,
    val amount: Long?,
    @field:Size(max = 1000) val comment: String?,
)

data class RefundRequest(val visitItemId: UUID?, @field:Size(max = 500) val reason: String?)

data class DebtResponse(val visitId: UUID, val visitNo: Long, val patient: PatientRef?, val createdAt: Instant, val items: List<BillableItem>, val total: Long)

@RestController
@RequestMapping("/api/v1/cashier")
class ShiftController(private val shifts: ShiftService, private val refunds: RefundService, private val patients: PatientQueries) {
    /** The signed-in cashier's open shift with live totals (X report); 204 when none is open. */
    @GetMapping("/shifts/current")
    @PreAuthorize(Permissions.HAS_SHIFT_MANAGE)
    fun current(): ShiftResponse? = shifts.current()?.let(::response)

    @PostMapping("/shifts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.HAS_SHIFT_MANAGE)
    fun open(@RequestBody body: OpenShiftRequest) = response(shifts.open(body.openingCash))

    @PostMapping("/shifts/{id}/close")
    @PreAuthorize(Permissions.HAS_SHIFT_MANAGE)
    fun close(@PathVariable id: UUID, @Valid @RequestBody body: CloseShiftRequest) =
        response(shifts.close(id, body.version ?: throw ValidationException("version", "validation.not_null"), body.countedCash, body.note))

    @PostMapping("/shifts/current/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.HAS_SHIFT_MANAGE)
    fun expense(@Valid @RequestBody body: ExpenseRequest) = response(shifts.addExpense(body.recipientStaffId, body.recipientName, body.amount, body.comment))

    @GetMapping("/shifts/{id}")
    @PreAuthorize("hasAnyAuthority('SHIFT_MANAGE', 'REPORTS_VIEW')")
    fun get(@PathVariable id: UUID) = response(shifts.get(id, CurrentPermissions.hasAny(Permission.REPORTS_VIEW)))

    /** Every cashier's shifts for a period (Admin's money control). */
    @GetMapping("/shifts")
    @PreAuthorize(Permissions.HAS_REPORTS_VIEW)
    fun list(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): List<ShiftSummary> {
        if (to.isBefore(from) || from.plusDays(366).isBefore(to)) throw ValidationException("to", "validation.invalid")
        return shifts.list(from, to).map { (s, t) ->
            val expected = s.expectedCash ?: t.expectedCash(s.openingCash)
            ShiftSummary(s.id, s.shiftNo, s.cashierName, s.status, s.openedAt, s.closedAt, t, expected, s.difference)
        }
    }

    /** People an expense can be paid to (any active staff member); "other" is typed in. */
    @GetMapping("/expense-recipients")
    @PreAuthorize(Permissions.HAS_SHIFT_MANAGE)
    fun recipients(): List<StaffRef> = shifts.recipients()

    @PostMapping("/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.HAS_REFUND)
    fun approve(@Valid @RequestBody body: RefundRequest) = refund(refunds.approve(body.visitItemId, body.reason))

    /** Refunds approved by the Admin, waiting for a cashier to give the cash back. */
    @GetMapping("/refunds/pending")
    @PreAuthorize("hasAnyAuthority('PAYMENT_ACCEPT', 'REFUND')")
    fun pending() = refunds.pending().map(::refund)

    @PostMapping("/refunds/{id}/pay-out")
    @PreAuthorize(Permissions.HAS_PAYMENT_ACCEPT)
    fun payOut(@PathVariable id: UUID, @RequestBody body: CloseShiftRequest) =
        refund(refunds.payOut(id, body.version ?: throw ValidationException("version", "validation.not_null")))

    /** Services the Admin allowed before payment and still unpaid. */
    @GetMapping("/debts")
    @PreAuthorize(Permissions.HAS_DEBT_VIEW)
    fun debts(): List<DebtResponse> {
        val list = refunds.debts()
        val people = patients.findAll(list.map { it.patientId })
        return list.map { DebtResponse(it.visitId, it.visitNo, people[it.patientId]?.let(::PatientRef), it.createdAt, it.items, it.total) }
    }

    private fun refund(r: Refund) = RefundResponse(
        r.id, r.serviceName, patients.find(r.patientId)?.let(::PatientRef), r.amount, r.originalMethod, r.reason, r.approvedByName,
        r.approvedAt, r.status, r.paidOutAt, r.version,
    )

    private fun response(r: ShiftReport) = ShiftResponse(
        r.shift.id, r.shift.shiftNo, r.shift.cashierName, r.shift.status, r.shift.openingCash, r.shift.openedAt, r.shift.closedAt,
        r.totals, r.expectedCash, r.shift.countedCash, r.shift.difference, r.shift.closeNote,
        r.expenses.map { ExpenseResponse(it.id, it.recipientName, it.amount, it.comment, it.createdAt) },
        r.refunds.map(::refund), r.shift.version,
    )
}
