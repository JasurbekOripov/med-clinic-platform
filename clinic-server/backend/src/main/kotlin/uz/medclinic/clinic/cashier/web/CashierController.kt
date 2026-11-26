package uz.medclinic.clinic.cashier.web

import jakarta.validation.Valid
import jakarta.validation.constraints.Size
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
import uz.medclinic.clinic.cashier.application.PaymentService
import uz.medclinic.clinic.cashier.domain.Payment
import uz.medclinic.clinic.cashier.domain.PaymentMethod
import uz.medclinic.clinic.patients.api.PatientQueries
import uz.medclinic.clinic.patients.api.PatientSummary
import uz.medclinic.clinic.shared.Permissions
import uz.medclinic.clinic.visits.api.BillableItem
import uz.medclinic.platform.domain.PageQuery
import uz.medclinic.platform.domain.ValidationException
import uz.medclinic.platform.web.PageResponse
import java.time.Instant
import java.util.UUID

data class PatientRef(val id: UUID, val cardNo: Long, val fullName: String) {
    constructor(p: PatientSummary) : this(p.id, p.cardNo, p.fullName)
}

data class UnpaidVisitResponse(
    val visitId: UUID,
    val visitNo: Long,
    val patient: PatientRef?,
    val createdAt: Instant,
    val items: List<BillableItem>,
    val total: Long,
)

data class PaymentLineResponse(val serviceName: String, val doctorName: String?, val price: Long, val discount: Long, val amount: Long)

data class PaymentResponse(
    val id: UUID,
    val receiptNo: Long,
    val visitId: UUID,
    val visitNo: Long,
    val patient: PatientRef?,
    val amount: Long,
    val method: PaymentMethod,
    val cashierName: String,
    val lines: List<PaymentLineResponse>,
    val createdAt: Instant,
)

data class PaymentRequest(
    val visitId: UUID?,
    @field:Size(min = 1, max = 100) val itemIds: Set<UUID> = emptySet(),
    val method: PaymentMethod?,
    /** The total shown to the cashier; guards against paying for a visit someone changed meanwhile. */
    val amount: Long?,
)

@RestController
@RequestMapping("/api/v1/cashier")
class CashierController(private val payments: PaymentService, private val patients: PatientQueries) {
    @GetMapping("/unpaid")
    @PreAuthorize(Permissions.HAS_PAYMENT_ACCEPT)
    fun unpaid(@RequestParam(required = false) page: Int?, @RequestParam(required = false) size: Int?): PageResponse<UnpaidVisitResponse> {
        val result = payments.unpaid(PageQuery.of(page, size))
        val people = patients.findAll(result.items.map { it.patientId })
        return PageResponse.of(result) { v ->
            UnpaidVisitResponse(v.visitId, v.visitNo, people[v.patientId]?.let(::PatientRef), v.createdAt, v.items, v.total)
        }
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.HAS_PAYMENT_ACCEPT)
    fun pay(@Valid @RequestBody body: PaymentRequest): PaymentResponse {
        val payment = payments.pay(
            body.visitId ?: throw ValidationException("visitId", "validation.not_null"),
            body.itemIds,
            body.method ?: throw ValidationException("method", "validation.not_null"),
            body.amount ?: throw ValidationException("amount", "validation.not_null"),
        )
        return response(payment)
    }

    /** One receipt, for (re)printing. */
    @GetMapping("/payments/{id}")
    @PreAuthorize(Permissions.HAS_PAYMENT_ACCEPT)
    fun get(@PathVariable id: UUID) = response(payments.get(id))

    @GetMapping("/payments")
    @PreAuthorize(Permissions.HAS_PAYMENT_ACCEPT)
    fun byVisit(@RequestParam visitId: UUID) = payments.byVisit(visitId).map(::response)

    private fun response(p: Payment) = PaymentResponse(
        p.id, p.receiptNo, p.visitId, p.visitNo, patients.find(p.patientId)?.let(::PatientRef), p.amount, p.method, p.cashierName,
        p.lines.map { PaymentLineResponse(it.serviceName, it.doctorName, it.price, it.discount, it.amount) }, p.createdAt,
    )
}
