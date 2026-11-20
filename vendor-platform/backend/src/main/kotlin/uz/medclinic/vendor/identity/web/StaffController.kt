package uz.medclinic.vendor.identity.web

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
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
import uz.medclinic.vendor.identity.application.CreateStaffCommand
import uz.medclinic.vendor.identity.application.StaffService
import uz.medclinic.vendor.identity.application.UpdateStaffCommand
import uz.medclinic.vendor.identity.domain.StaffAccount
import uz.medclinic.vendor.identity.domain.VendorRole
import uz.medclinic.vendor.shared.domain.PageQuery
import uz.medclinic.vendor.shared.security.Permissions
import uz.medclinic.vendor.shared.web.PageResponse
import java.time.Instant
import java.util.UUID

data class StaffResponse(
    val id: UUID,
    val login: String,
    val fullName: String,
    val role: VendorRole,
    val active: Boolean,
    val mustChangePassword: Boolean,
    val lastLoginAt: Instant?,
    val createdAt: Instant,
    val version: Int,
) {
    constructor(s: StaffAccount) : this(s.id, s.login, s.fullName, s.role, s.active, s.mustChangePassword, s.lastLoginAt, s.createdAt, s.version)
}

data class StaffWithPasswordResponse(val staff: StaffResponse, val temporaryPassword: String)

data class CreateStaffRequest(
    @field:NotBlank @field:Size(max = 50) val login: String,
    @field:NotBlank @field:Size(max = 200) val fullName: String,
    @field:NotNull val role: VendorRole,
)

data class UpdateStaffRequest(
    @field:NotBlank @field:Size(max = 200) val fullName: String,
    @field:NotNull val role: VendorRole,
    @field:PositiveOrZero val version: Int,
)

/** Body of state-changing actions that need optimistic locking. */
data class VersionRequest(@field:PositiveOrZero val version: Int)

@RestController
@RequestMapping("/api/v1/staff")
@PreAuthorize(Permissions.HAS_STAFF_MANAGE)
class StaffController(private val staff: StaffService) {
    @GetMapping
    fun list(
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "false") includeInactive: Boolean,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ) = PageResponse.of(staff.search(q, includeInactive, PageQuery.of(page, size)), ::StaffResponse)

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID) = StaffResponse(staff.get(id))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody body: CreateStaffRequest): StaffWithPasswordResponse {
        val result = staff.create(CreateStaffCommand(body.login, body.fullName, body.role))
        return StaffWithPasswordResponse(StaffResponse(result.staff), result.temporaryPassword)
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @Valid @RequestBody body: UpdateStaffRequest) =
        StaffResponse(staff.update(UpdateStaffCommand(id, body.fullName, body.role, body.version)))

    @PostMapping("/{id}/deactivate")
    fun deactivate(@PathVariable id: UUID, @Valid @RequestBody body: VersionRequest) = StaffResponse(staff.deactivate(id, body.version))

    @PostMapping("/{id}/activate")
    fun activate(@PathVariable id: UUID, @Valid @RequestBody body: VersionRequest) = StaffResponse(staff.activate(id, body.version))

    @PostMapping("/{id}/reset-password")
    fun resetPassword(@PathVariable id: UUID, @Valid @RequestBody body: VersionRequest): StaffWithPasswordResponse {
        val result = staff.resetPassword(id, body.version)
        return StaffWithPasswordResponse(StaffResponse(result.staff), result.temporaryPassword)
    }
}
