package uz.medclinic.clinic.cashier.domain

import java.time.Instant
import java.util.UUID

/** Card terminals (Uzcard, Humo, Visa…) are all "card" here; the bank statement is the place to tell them apart. */
enum class PaymentMethod { CASH, CARD, TRANSFER }

data class PaymentLine(
    val visitItemId: UUID,
    val serviceName: String,
    val doctorName: String?,
    val price: Long,
    val discount: Long,
    val amount: Long,
)

data class Payment(
    val id: UUID,
    val receiptNo: Long,
    val visitId: UUID,
    val visitNo: Long,
    val patientId: UUID,
    val method: PaymentMethod,
    val cashierId: UUID,
    val cashierName: String,
    val lines: List<PaymentLine>,
    val createdAt: Instant,
) {
    init {
        require(lines.isNotEmpty())
    }

    val amount: Long get() = lines.sumOf { it.amount }
}

interface PaymentRepository {
    /** Assigns the receipt number. */
    fun insert(payment: Payment): Payment
    fun findById(id: UUID): Payment?
    fun findByVisit(visitId: UUID): List<Payment>
}
