package uz.medclinic.clinic.catalog.web

import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uz.medclinic.clinic.catalog.application.CatalogService
import uz.medclinic.clinic.catalog.domain.DiscountDetails
import uz.medclinic.clinic.catalog.domain.DiscountType
import uz.medclinic.clinic.catalog.domain.ServiceDetails
import uz.medclinic.clinic.catalog.domain.ServiceGroup
import uz.medclinic.clinic.catalog.domain.ServiceItem
import uz.medclinic.clinic.identity.api.StaffRef
import uz.medclinic.clinic.shared.Permissions
import uz.medclinic.platform.domain.ValidationException
import java.util.UUID

data class ServiceGroupResponse(val id: UUID, val name: String, val sortOrder: Int, val version: Int) {
    constructor(g: ServiceGroup) : this(g.id, g.name, g.sortOrder, g.version)
}

data class ServiceGroupRequest(@field:Size(max = 100) val name: String?, val sortOrder: Int = 0, val version: Int? = null)

data class DoctorPrice(val staffId: UUID, val price: Long)

data class ServiceResponse(
    val id: UUID,
    val groupId: UUID,
    val code: String?,
    val name: String,
    val price: Long,
    val perDoctor: Boolean,
    val doctorPrices: List<DoctorPrice>,
    val active: Boolean,
    val version: Int,
) {
    constructor(s: ServiceItem) : this(
        s.id, s.details.groupId, s.details.code, s.details.name, s.details.price, s.details.perDoctor,
        s.details.doctorPrices.map { DoctorPrice(it.key, it.value) }.sortedBy { it.staffId }, s.details.active, s.version,
    )
}

data class ServiceRequest(
    val groupId: UUID?,
    @field:Size(max = 20) val code: String?,
    @field:Size(max = 200) val name: String?,
    val price: Long?,
    val perDoctor: Boolean = false,
    @field:Size(max = 500) val doctorPrices: List<DoctorPrice> = emptyList(),
    val active: Boolean = true,
    val version: Int? = null,
)

data class DoctorResponse(val id: UUID, val fullName: String) {
    constructor(s: StaffRef) : this(s.id, s.fullName)
}

data class DiscountTypeResponse(val id: UUID, val name: String, val percent: Int, val active: Boolean, val version: Int) {
    constructor(d: DiscountType) : this(d.id, d.name, d.percent, d.active, d.version)
}

data class DiscountTypeRequest(@field:Size(max = 100) val name: String?, val percent: Int?, val active: Boolean = true, val version: Int? = null)

/** Price list. Reading: managers, reception and cashiers; changing: `PRICE_LIST_MANAGE` (Admin) only. */
@RestController
@RequestMapping("/api/v1/catalog")
class CatalogController(private val catalog: CatalogService) {
    @GetMapping("/groups")
    @PreAuthorize(Permissions.HAS_CATALOG_READ)
    fun groups() = catalog.groups().map(::ServiceGroupResponse)

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.HAS_PRICE_LIST_MANAGE)
    fun createGroup(@Valid @RequestBody body: ServiceGroupRequest) = ServiceGroupResponse(catalog.createGroup(body.name, body.sortOrder))

    @PutMapping("/groups/{id}")
    @PreAuthorize(Permissions.HAS_PRICE_LIST_MANAGE)
    fun updateGroup(@PathVariable id: UUID, @Valid @RequestBody body: ServiceGroupRequest) =
        ServiceGroupResponse(catalog.updateGroup(id, version(body.version), body.name, body.sortOrder))

    @DeleteMapping("/groups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Permissions.HAS_PRICE_LIST_MANAGE)
    fun deleteGroup(@PathVariable id: UUID, @RequestParam version: Int) = catalog.deleteGroup(id, version)

    @GetMapping("/services")
    @PreAuthorize(Permissions.HAS_CATALOG_READ)
    fun services() = catalog.services().map(::ServiceResponse)

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.HAS_PRICE_LIST_MANAGE)
    fun createService(@Valid @RequestBody body: ServiceRequest) = ServiceResponse(catalog.createService(details(body)))

    @PutMapping("/services/{id}")
    @PreAuthorize(Permissions.HAS_PRICE_LIST_MANAGE)
    fun updateService(@PathVariable id: UUID, @Valid @RequestBody body: ServiceRequest) =
        ServiceResponse(catalog.updateService(id, version(body.version), details(body)))

    @DeleteMapping("/services/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Permissions.HAS_PRICE_LIST_MANAGE)
    fun deleteService(@PathVariable id: UUID, @RequestParam version: Int) = catalog.deleteService(id, version)

    /** Staff who can have an own consultation price (and later: who can be chosen as the visit's doctor). */
    @GetMapping("/doctors")
    @PreAuthorize(Permissions.HAS_CATALOG_READ)
    fun doctors() = catalog.doctors().map(::DoctorResponse)

    @GetMapping("/discounts")
    @PreAuthorize(Permissions.HAS_CATALOG_READ)
    fun discounts() = catalog.discounts().map(::DiscountTypeResponse)

    @PostMapping("/discounts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.HAS_PRICE_LIST_MANAGE)
    fun createDiscount(@Valid @RequestBody body: DiscountTypeRequest) =
        DiscountTypeResponse(catalog.createDiscount(DiscountDetails.of(body.name, body.percent, body.active)))

    @PutMapping("/discounts/{id}")
    @PreAuthorize(Permissions.HAS_PRICE_LIST_MANAGE)
    fun updateDiscount(@PathVariable id: UUID, @Valid @RequestBody body: DiscountTypeRequest) =
        DiscountTypeResponse(catalog.updateDiscount(id, version(body.version), DiscountDetails.of(body.name, body.percent, body.active)))

    @DeleteMapping("/discounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Permissions.HAS_PRICE_LIST_MANAGE)
    fun deleteDiscount(@PathVariable id: UUID, @RequestParam version: Int) = catalog.deleteDiscount(id, version)

    private fun version(v: Int?) = v ?: throw ValidationException("version", "validation.not_null")

    private fun details(b: ServiceRequest): ServiceDetails {
        if (b.doctorPrices.map { it.staffId }.toSet().size != b.doctorPrices.size) {
            throw ValidationException("doctorPrices", "catalog.duplicate_doctor")
        }
        return ServiceDetails.of(
            b.groupId, b.code, b.name, b.price, b.perDoctor, b.doctorPrices.associate { it.staffId to it.price }, b.active,
        )
    }
}
