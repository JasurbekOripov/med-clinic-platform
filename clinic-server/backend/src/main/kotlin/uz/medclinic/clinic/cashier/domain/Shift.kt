package uz.medclinic.clinic.cashier.domain

import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.domain.FieldViolation
import uz.medclinic.platform.domain.Text
import uz.medclinic.platform.domain.ValidationException
import java.time.Instant
import java.util.UUID

/** Sanity limit for any single cash amount typed by hand (1 billion UZS). */
const val MAX_CASH: Long = 1_000_000_000

enum class ShiftStatus { OPEN, CLOSED }

data class Shift(
    val id: UUID,
    val shiftNo: Long,
    val cashierId: UUID,
    val cashierName: String,
    val openingCash: Long,
    val openedAt: Instant,
    val status: ShiftStatus,
    val closedAt: Instant?,
    val expectedCash: Long?,
    val countedCash: Long?,
    val closeNote: String?,
    val version: Int,
) {
    /** Z report: the counted cash is compared with what should be in the drawer; the shift is then locked. */
    fun close(expected: Long, counted: Long?, note: String?, now: Instant): Shift {
        if (status != ShiftStatus.OPEN) throw DomainException("shift.not_open")
        val c = cashAmount("countedCash", counted, allowZero = true)
        val cleanNote = Text.cleanMultiline(note)
        if (cleanNote != null && cleanNote.length > 500) throw ValidationException("closeNote", "validation.size", mapOf("max" to 500))
        return copy(status = ShiftStatus.CLOSED, closedAt = now, expectedCash = expected, countedCash = c, closeNote = cleanNote)
    }

    /** Counted minus expected: negative = shortage, positive = surplus. */
    val difference: Long? get() = if (countedCash != null && expectedCash != null) countedCash - expectedCash else null
}

/** Money moved in one shift. Refunds are always paid out in cash (owner's decision). */
data class ShiftTotals(
    val cash: Long,
    val card: Long,
    val transfer: Long,
    val payments: Int,
    val refunds: Long,
    val refundCount: Int,
    val expenses: Long,
    val expenseCount: Int,
) {
    fun expectedCash(opening: Long): Long = opening + cash - refunds - expenses

    companion object {
        val EMPTY = ShiftTotals(0, 0, 0, 0, 0, 0, 0, 0)
    }
}

/** Cash paid out from the drawer (to a doctor or anyone else); the comment is mandatory. */
data class Expense(
    val id: UUID,
    val shiftId: UUID,
    val recipientStaffId: UUID?,
    val recipientName: String,
    val amount: Long,
    val comment: String,
    val createdBy: UUID,
    val createdAt: Instant,
) {
    companion object {
        fun validated(recipientName: String?, amount: Long?, comment: String?): Triple<String, Long, String> {
            val errors = mutableListOf<FieldViolation>()
            val name = Text.clean(recipientName)
            if (name == null) errors += FieldViolation("recipientName", "validation.not_blank")
            else if (name.length > 200) errors += FieldViolation("recipientName", "validation.size", mapOf("max" to 200))
            val sum = runCatching { cashAmount("amount", amount, allowZero = false) }.getOrElse {
                errors += (it as ValidationException).violations
                0L
            }
            val text = Text.cleanMultiline(comment)
            if (text == null) errors += FieldViolation("comment", "validation.not_blank")
            else if (text.length > 1000) errors += FieldViolation("comment", "validation.size", mapOf("max" to 1000))
            if (errors.isNotEmpty()) throw ValidationException(errors)
            return Triple(name!!, sum, text!!)
        }
    }
}

enum class RefundStatus { APPROVED, PAID_OUT }

data class Refund(
    val id: UUID,
    val paymentId: UUID,
    val visitId: UUID,
    val visitItemId: UUID,
    val patientId: UUID,
    val serviceName: String,
    val amount: Long,
    val originalMethod: PaymentMethod,
    val reason: String,
    val approvedBy: UUID,
    val approvedByName: String,
    val approvedAt: Instant,
    val status: RefundStatus,
    val shiftId: UUID?,
    val paidOutBy: UUID?,
    val paidOutAt: Instant?,
    val version: Int,
) {
    fun paidOut(shift: Shift, by: UUID, now: Instant): Refund {
        if (status != RefundStatus.APPROVED) throw DomainException("refund.already_paid_out")
        if (shift.status != ShiftStatus.OPEN) throw DomainException("shift.not_open")
        return copy(status = RefundStatus.PAID_OUT, shiftId = shift.id, paidOutBy = by, paidOutAt = now)
    }

    companion object {
        fun reason(raw: String?): String {
            val clean = Text.clean(raw) ?: throw ValidationException("reason", "validation.not_blank")
            if (clean.length > 500) throw ValidationException("reason", "validation.size", mapOf("max" to 500))
            return clean
        }
    }
}

fun cashAmount(field: String, value: Long?, allowZero: Boolean): Long = when {
    value == null -> throw ValidationException(field, "validation.not_null")
    value < (if (allowZero) 0 else 1) || value > MAX_CASH ->
        throw ValidationException(field, "validation.range", mapOf("min" to if (allowZero) 0 else 1, "max" to MAX_CASH))
    else -> value
}

interface ShiftRepository {
    /** Assigns the shift number; `shift.already_open` if the cashier has an open shift. */
    fun insert(shift: Shift): Shift
    fun findById(id: UUID): Shift?
    fun findOpen(cashierId: UUID): Shift?

    /** Locks the shift row until the transaction ends (closing vs. a payment arriving at the same moment). */
    fun lock(id: UUID): Shift?
    fun update(shift: Shift): Shift
    fun totals(shiftId: UUID): ShiftTotals
    fun list(from: Instant, to: Instant): List<Shift>
}

interface ExpenseRepository {
    fun insert(expense: Expense)
    fun byShift(shiftId: UUID): List<Expense>
}

interface RefundRepository {
    fun insert(refund: Refund)
    fun findById(id: UUID): Refund?
    fun update(refund: Refund): Refund
    fun pending(): List<Refund>
    fun byShift(shiftId: UUID): List<Refund>
}
