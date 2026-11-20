package uz.medclinic.vendor.tenancy.web

import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
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
import uz.medclinic.vendor.shared.domain.PageQuery
import uz.medclinic.vendor.shared.security.Permissions
import uz.medclinic.vendor.shared.web.PageResponse
import uz.medclinic.vendor.tenancy.api.TenantStatus
import uz.medclinic.vendor.tenancy.application.TenancyService
import uz.medclinic.vendor.tenancy.domain.Branch
import uz.medclinic.vendor.tenancy.domain.BranchDetails
import uz.medclinic.vendor.tenancy.domain.Clinic
import uz.medclinic.vendor.tenancy.domain.ClinicDetails
import uz.medclinic.vendor.tenancy.domain.ClinicFilter
import uz.medclinic.vendor.tenancy.domain.Region
import java.time.Instant
import java.util.UUID

/** Field shape only; content rules (required, phone format, trimming) live in the domain. */
data class ClinicRequest(
    @field:Size(max = 200) val name: String?,
    @field:Size(max = 300) val legalName: String?,
    @field:Size(max = 200) val ownerName: String?,
    @field:Size(max = 32) val phone: String?,
    @field:Size(max = 500) val address: String?,
    val region: Region?,
    @field:Size(max = 4000) val notes: String?,
    /** Required on update, ignored on create. */
    @field:PositiveOrZero val version: Int? = null,
) {
    fun details() = ClinicDetails.of(name, legalName, ownerName, phone, address, region, notes)
}

data class BranchRequest(
    @field:Size(max = 200) val name: String?,
    @field:Size(max = 500) val address: String?,
    @field:Size(max = 32) val phone: String?,
    @field:PositiveOrZero val version: Int? = null,
) {
    fun details() = BranchDetails.of(name, address, phone)
}

data class StatusRequest(@field:NotNull val status: TenantStatus, @field:PositiveOrZero val version: Int)

data class ClinicResponse(
    val id: UUID,
    val code: String,
    val name: String,
    val legalName: String?,
    val ownerName: String,
    val phone: String,
    val address: String?,
    val region: Region,
    val notes: String?,
    val status: TenantStatus,
    val branchCount: Int?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Int,
) {
    constructor(c: Clinic, branchCount: Int? = null) : this(
        c.id, c.code, c.details.name, c.details.legalName, c.details.ownerName, c.details.phone.e164, c.details.address,
        c.details.region, c.details.notes, c.status, branchCount, c.createdAt, c.updatedAt, c.version,
    )
}

data class BranchResponse(
    val id: UUID,
    val clinicId: UUID,
    val code: String,
    val name: String,
    val address: String?,
    val phone: String?,
    val status: TenantStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Int,
) {
    constructor(b: Branch) : this(
        b.id, b.clinicId, b.code, b.details.name, b.details.address, b.details.phone?.e164, b.status, b.createdAt, b.updatedAt, b.version,
    )
}

private fun requireVersion(version: Int?): Int =
    version ?: throw uz.medclinic.vendor.shared.domain.ValidationException("version", "validation.not_null")

@RestController
@RequestMapping("/api/v1/clinics")
class ClinicController(private val tenancy: TenancyService) {
    @GetMapping
    @PreAuthorize(Permissions.HAS_CLINIC_VIEW)
    fun search(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: TenantStatus?,
        @RequestParam(required = false) region: Region?,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ) = PageResponse.of(tenancy.searchClinics(ClinicFilter(q, status, region), PageQuery.of(page, size))) {
        ClinicResponse(it.clinic, it.branchCount)
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permissions.HAS_CLINIC_VIEW)
    fun get(@PathVariable id: UUID) = ClinicResponse(tenancy.getClinic(id))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.HAS_CLINIC_EDIT)
    fun create(@Valid @RequestBody body: ClinicRequest) = ClinicResponse(tenancy.createClinic(body.details()))

    @PutMapping("/{id}")
    @PreAuthorize(Permissions.HAS_CLINIC_EDIT)
    fun update(@PathVariable id: UUID, @Valid @RequestBody body: ClinicRequest) =
        ClinicResponse(tenancy.updateClinic(id, requireVersion(body.version), body.details()))

    @PostMapping("/{id}/status")
    @PreAuthorize(Permissions.HAS_CLINIC_STATUS)
    fun changeStatus(@PathVariable id: UUID, @Valid @RequestBody body: StatusRequest) =
        ClinicResponse(tenancy.changeClinicStatus(id, body.version, body.status))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Permissions.HAS_CLINIC_STATUS)
    fun delete(@PathVariable id: UUID, @RequestParam version: Int) = tenancy.deleteClinic(id, version)

    @GetMapping("/{id}/branches")
    @PreAuthorize(Permissions.HAS_CLINIC_VIEW)
    fun branches(@PathVariable id: UUID, @RequestParam(defaultValue = "true") includeArchived: Boolean) =
        tenancy.listBranches(id, includeArchived).map(::BranchResponse)

    @PostMapping("/{id}/branches")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.HAS_CLINIC_EDIT)
    fun createBranch(@PathVariable id: UUID, @Valid @RequestBody body: BranchRequest) =
        BranchResponse(tenancy.createBranch(id, body.details()))
}

@RestController
@RequestMapping("/api/v1/branches")
class BranchController(private val tenancy: TenancyService) {
    @GetMapping("/{id}")
    @PreAuthorize(Permissions.HAS_CLINIC_VIEW)
    fun get(@PathVariable id: UUID) = BranchResponse(tenancy.getBranch(id))

    @PutMapping("/{id}")
    @PreAuthorize(Permissions.HAS_CLINIC_EDIT)
    fun update(@PathVariable id: UUID, @Valid @RequestBody body: BranchRequest) =
        BranchResponse(tenancy.updateBranch(id, requireVersion(body.version), body.details()))

    @PostMapping("/{id}/status")
    @PreAuthorize(Permissions.HAS_CLINIC_STATUS)
    fun changeStatus(@PathVariable id: UUID, @Valid @RequestBody body: StatusRequest) =
        BranchResponse(tenancy.changeBranchStatus(id, body.version, body.status))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Permissions.HAS_CLINIC_STATUS)
    fun delete(@PathVariable id: UUID, @RequestParam version: Int) = tenancy.deleteBranch(id, version)
}
