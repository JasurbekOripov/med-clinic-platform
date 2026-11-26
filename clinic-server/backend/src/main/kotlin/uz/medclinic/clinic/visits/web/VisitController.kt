package uz.medclinic.clinic.visits.web

import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uz.medclinic.clinic.shared.CurrentPermissions
import uz.medclinic.clinic.shared.Permission
import uz.medclinic.clinic.shared.Permissions
import uz.medclinic.clinic.visits.application.ServiceOrder
import uz.medclinic.clinic.visits.application.VisitService
import uz.medclinic.clinic.visits.domain.ItemStatus
import uz.medclinic.clinic.visits.domain.Visit
import uz.medclinic.clinic.visits.domain.VisitItem
import uz.medclinic.platform.domain.NotFoundException
import uz.medclinic.platform.domain.ValidationException
import java.time.Instant
import java.util.UUID

data class VisitItemResponse(
    val id: UUID,
    val serviceId: UUID,
    val serviceName: String,
    val groupName: String,
    val doctorId: UUID?,
    val doctorName: String?,
    val price: Long,
    val discount: Long,
    val amount: Long,
    val status: ItemStatus,
    val paidAt: Instant?,
    val allowed: Boolean,
    val allowReason: String?,
    val cancelReason: String?,
    val createdAt: Instant,
) {
    constructor(i: VisitItem) : this(
        i.id, i.serviceId, i.serviceName, i.groupName, i.doctorId, i.doctorName, i.price, i.discount, i.amount, i.status,
        i.paidAt, i.allowance != null, i.allowance?.reason, i.cancellation?.reason, i.createdAt,
    )
}

data class VisitDiscountResponse(val typeId: UUID, val name: String, val percent: Int)

data class VisitAgentResponse(val id: UUID, val name: String, val fee: Long)

data class VisitResponse(
    val id: UUID,
    val visitNo: Long,
    val patientId: UUID,
    val discount: VisitDiscountResponse?,
    val agent: VisitAgentResponse?,
    val items: List<VisitItemResponse>,
    val total: Long,
    val paid: Long,
    val unpaid: Long,
    val createdAt: Instant,
    val version: Int,
)

data class ServiceOrderRequest(val serviceId: UUID?, val doctorId: UUID? = null)

data class CreateVisitRequest(
    val patientId: UUID?,
    val discountTypeId: UUID? = null,
    val agentId: UUID? = null,
    @field:Size(max = 100) val services: List<ServiceOrderRequest> = emptyList(),
)

data class AddServiceRequest(val serviceId: UUID?, val doctorId: UUID? = null, val version: Int?)

data class CancelServiceRequest(@field:Size(max = 500) val reason: String?, val version: Int?)

data class UpdateVisitRequest(val discountTypeId: UUID? = null, val agentId: UUID? = null, val version: Int?)

data class AllowRequest(@field:Size(min = 1, max = 100) val itemIds: Set<UUID> = emptySet(), @field:Size(max = 500) val reason: String?, val version: Int?)

/**
 * Visits at the reception desk. Anyone who may see patients may read visits, but staff who neither register nor take
 * money (doctors, lab, nurses) only get paid services and those the Admin allowed: the owner's rule that unpaid
 * patients are invisible to them is enforced here, on the server.
 */
@RestController
@RequestMapping("/api/v1/visits")
class VisitController(private val visits: VisitService) {
    @GetMapping
    @PreAuthorize(Permissions.HAS_PATIENT_VIEW)
    fun byPatient(@RequestParam patientId: UUID): List<VisitResponse> = visits.byPatient(patientId).mapNotNull(::view)

    @GetMapping("/{id}")
    @PreAuthorize(Permissions.HAS_PATIENT_VIEW)
    fun get(@PathVariable id: UUID): VisitResponse = view(visits.get(id)) ?: throw NotFoundException("visit", id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.HAS_VISIT_CREATE)
    fun create(@Valid @RequestBody body: CreateVisitRequest): VisitResponse {
        val patientId = body.patientId ?: throw ValidationException("patientId", "validation.not_null")
        val orders = body.services.mapIndexed { i, s -> ServiceOrder(s.serviceId ?: throw ValidationException("services[$i].serviceId", "validation.not_null"), s.doctorId) }
        return full(visits.create(patientId, body.discountTypeId, body.agentId, orders))
    }

    @PostMapping("/{id}/services")
    @PreAuthorize(Permissions.HAS_VISIT_CREATE)
    fun addService(@PathVariable id: UUID, @Valid @RequestBody body: AddServiceRequest) = full(
        visits.addService(id, version(body.version), ServiceOrder(body.serviceId ?: throw ValidationException("serviceId", "validation.not_null"), body.doctorId)),
    )

    @PostMapping("/{id}/services/{itemId}/cancel")
    @PreAuthorize(Permissions.HAS_VISIT_EDIT_UNPAID)
    fun cancelService(@PathVariable id: UUID, @PathVariable itemId: UUID, @Valid @RequestBody body: CancelServiceRequest) =
        full(visits.cancelService(id, version(body.version), itemId, body.reason))

    @PutMapping("/{id}")
    @PreAuthorize(Permissions.HAS_VISIT_EDIT_UNPAID)
    fun update(@PathVariable id: UUID, @Valid @RequestBody body: UpdateVisitRequest) =
        full(visits.update(id, version(body.version), body.discountTypeId, body.agentId))

    @PostMapping("/{id}/allow")
    @PreAuthorize(Permissions.HAS_EMERGENCY_ALLOW)
    fun allow(@PathVariable id: UUID, @Valid @RequestBody body: AllowRequest) = full(visits.allow(id, version(body.version), body.itemIds, body.reason))

    private fun version(v: Int?) = v ?: throw ValidationException("version", "validation.not_null")

    /** Null when nothing of the visit is visible to the caller. */
    private fun view(v: Visit): VisitResponse? {
        if (seesUnpaid()) return full(v)
        val visible = v.items.filter { it.visibleToMedicalStaff }
        if (visible.isEmpty()) return null
        val paid = visible.filter { it.status == ItemStatus.PAID }.sumOf { it.amount }
        val total = visible.sumOf { it.amount }
        // Discount and referral details are reception/cashier business.
        return VisitResponse(v.id, v.visitNo, v.patientId, null, null, visible.map(::VisitItemResponse), total, paid, total - paid, v.createdAt, v.version)
    }

    private fun full(v: Visit) = VisitResponse(
        v.id, v.visitNo, v.patientId,
        v.discount?.let { VisitDiscountResponse(it.typeId, it.name, it.percent) },
        v.agent?.let { VisitAgentResponse(it.id, it.name, it.fee) },
        v.items.map(::VisitItemResponse), v.total, v.paid, v.unpaid, v.createdAt, v.version,
    )

    private fun seesUnpaid() = CurrentPermissions.hasAny(
        Permission.VISIT_CREATE, Permission.VISIT_EDIT_UNPAID, Permission.PAYMENT_ACCEPT, Permission.EMERGENCY_ALLOW,
    )
}
