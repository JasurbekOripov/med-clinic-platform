package uz.medclinic.clinic.identity.web

import jakarta.validation.Valid
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import uz.medclinic.clinic.identity.application.StaffCommand
import uz.medclinic.clinic.identity.application.StaffService
import uz.medclinic.clinic.identity.domain.SignatureImage
import uz.medclinic.clinic.identity.domain.StaffFilter
import uz.medclinic.clinic.identity.domain.StaffMember
import uz.medclinic.clinic.shared.Permissions
import uz.medclinic.platform.domain.PageQuery
import uz.medclinic.platform.domain.ValidationException
import uz.medclinic.platform.web.PageResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class StaffResponse(
    val id: UUID,
    val login: String,
    val fullName: String,
    val position: String?,
    val phone: String?,
    val roleId: UUID,
    val active: Boolean,
    val mustChangePassword: Boolean,
    val granted: List<String>,
    val denied: List<String>,
    val hasSignature: Boolean,
    val lastLoginAt: Instant?,
    val createdAt: Instant,
    val version: Int,
) {
    constructor(s: StaffMember) : this(
        s.id, s.login, s.profile.fullName, s.profile.position, s.profile.phone?.e164, s.roleId, s.active, s.mustChangePassword,
        s.granted.map { it.name }.sorted(), s.denied.map { it.name }.sorted(), s.hasSignature, s.lastLoginAt, s.createdAt, s.version,
    )
}

data class StaffWithPasswordResponse(val staff: StaffResponse, val temporaryPassword: String)

data class StaffRequest(
    @field:Size(max = 50) val login: String? = null,
    @field:Size(max = 200) val fullName: String?,
    @field:Size(max = 100) val position: String?,
    @field:Size(max = 32) val phone: String?,
    val roleId: UUID?,
    @field:Size(max = 100) val granted: List<String> = emptyList(),
    @field:Size(max = 100) val denied: List<String> = emptyList(),
    @field:PositiveOrZero val version: Int? = null,
) {
    fun command() = StaffCommand(
        fullName, position, phone,
        roleId ?: throw ValidationException("roleId", "validation.not_null"),
        granted, denied,
    )
}

data class StaffVersionRequest(@field:PositiveOrZero val version: Int)

@RestController
@RequestMapping("/api/v1/staff")
class StaffController(private val staff: StaffService) {
    @GetMapping
    @PreAuthorize(Permissions.HAS_STAFF_VIEW)
    fun list(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) roleId: UUID?,
        @RequestParam(defaultValue = "false") includeInactive: Boolean,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ) = PageResponse.of(staff.search(StaffFilter(q, roleId, includeInactive), PageQuery.of(page, size)), ::StaffResponse)

    @GetMapping("/{id}")
    @PreAuthorize(Permissions.HAS_STAFF_VIEW)
    fun get(@PathVariable id: UUID) = StaffResponse(staff.get(id))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.HAS_STAFF_MANAGE)
    fun create(@Valid @RequestBody body: StaffRequest): StaffWithPasswordResponse {
        val result = staff.create(body.login, body.command())
        return StaffWithPasswordResponse(StaffResponse(result.staff), result.temporaryPassword)
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permissions.HAS_STAFF_MANAGE)
    fun update(@PathVariable id: UUID, @Valid @RequestBody body: StaffRequest) =
        StaffResponse(staff.update(id, body.version ?: throw ValidationException("version", "validation.not_null"), body.command()))

    @PostMapping("/{id}/deactivate")
    @PreAuthorize(Permissions.HAS_STAFF_MANAGE)
    fun deactivate(@PathVariable id: UUID, @Valid @RequestBody body: StaffVersionRequest) = StaffResponse(staff.deactivate(id, body.version))

    @PostMapping("/{id}/activate")
    @PreAuthorize(Permissions.HAS_STAFF_MANAGE)
    fun activate(@PathVariable id: UUID, @Valid @RequestBody body: StaffVersionRequest) = StaffResponse(staff.activate(id, body.version))

    @PostMapping("/{id}/reset-password")
    @PreAuthorize(Permissions.HAS_STAFF_MANAGE)
    fun resetPassword(@PathVariable id: UUID, @Valid @RequestBody body: StaffVersionRequest): StaffWithPasswordResponse {
        val result = staff.resetPassword(id, body.version)
        return StaffWithPasswordResponse(StaffResponse(result.staff), result.temporaryPassword)
    }

    @GetMapping("/{id}/signature")
    @PreAuthorize(Permissions.HAS_STAFF_VIEW)
    fun signature(@PathVariable id: UUID): ResponseEntity<ByteArray> {
        val (bytes, type) = staff.signature(id)
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type))
            .cacheControl(CacheControl.maxAge(Duration.ZERO).cachePrivate().mustRevalidate()).body(bytes)
    }

    @PutMapping("/{id}/signature", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Permissions.HAS_STAFF_MANAGE)
    fun uploadSignature(@PathVariable id: UUID, @RequestPart("file") file: MultipartFile) {
        if (file.size > SignatureImage.MAX_BYTES) throw ValidationException("file", "validation.image_size", mapOf("maxKb" to SignatureImage.MAX_BYTES / 1024))
        staff.setSignature(id, file.bytes)
    }

    @DeleteMapping("/{id}/signature")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Permissions.HAS_STAFF_MANAGE)
    fun removeSignature(@PathVariable id: UUID) = staff.removeSignature(id)
}
