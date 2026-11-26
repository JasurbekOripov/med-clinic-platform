package uz.medclinic.clinic.settings.web

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
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import uz.medclinic.clinic.settings.application.SettingsService
import uz.medclinic.clinic.settings.domain.ClinicSettings
import uz.medclinic.clinic.settings.domain.LogoImage
import uz.medclinic.clinic.settings.domain.WorkingDay
import uz.medclinic.clinic.shared.Permissions
import uz.medclinic.platform.domain.ValidationException
import java.time.Duration
import java.time.Instant

data class SettingsResponse(
    val name: String,
    val legalName: String?,
    val address: String?,
    val phone: String?,
    val letterhead: String?,
    val receiptHeader: String?,
    val receiptFooter: String?,
    val workingHours: List<WorkingDay>,
    val idleLockMinutes: Int,
    val hasLogo: Boolean,
    val updatedAt: Instant,
    val version: Int,
) {
    constructor(s: ClinicSettings) : this(
        s.name, s.legalName, s.address, s.phone?.e164, s.letterhead, s.receiptHeader, s.receiptFooter, s.workingHours,
        s.idleLockMinutes, s.hasLogo, s.updatedAt, s.version,
    )
}

/** What the login screen and the app shell need before/after sign-in; contains nothing sensitive. */
/** What printed documents (receipts, later results and letters) show about the clinic; nothing secret. */
data class PrintSettingsResponse(
    val name: String,
    val legalName: String?,
    val address: String?,
    val phone: String?,
    val letterhead: String?,
    val receiptHeader: String?,
    val receiptFooter: String?,
    val hasLogo: Boolean,
) {
    constructor(s: ClinicSettings) : this(s.name, s.legalName, s.address, s.phone?.e164, s.letterhead, s.receiptHeader, s.receiptFooter, s.hasLogo)
}

data class PublicSettingsResponse(val name: String?, val hasLogo: Boolean, val idleLockMinutes: Int)

data class SettingsRequest(
    @field:Size(max = 200) val name: String?,
    @field:Size(max = 300) val legalName: String?,
    @field:Size(max = 500) val address: String?,
    @field:Size(max = 32) val phone: String?,
    @field:Size(max = 2000) val letterhead: String?,
    @field:Size(max = 1000) val receiptHeader: String?,
    @field:Size(max = 1000) val receiptFooter: String?,
    @field:Size(max = 7) val workingHours: List<WorkingDay>?,
    val idleLockMinutes: Int?,
    @field:PositiveOrZero val version: Int?,
)

@RestController
@RequestMapping("/api/v1/settings")
class SettingsController(private val settings: SettingsService) {
    @GetMapping("/public")
    fun publicSettings(): PublicSettingsResponse {
        val s = settings.findOrNull()
        return PublicSettingsResponse(s?.name, s?.hasLogo ?: false, s?.idleLockMinutes ?: ClinicSettings.DEFAULT_IDLE_LOCK_MINUTES)
    }

    @GetMapping("/print")
    @PreAuthorize(Permissions.HAS_PRINTING)
    fun print() = PrintSettingsResponse(settings.get())

    @GetMapping
    @PreAuthorize(Permissions.HAS_SETTINGS_MANAGE)
    fun get() = SettingsResponse(settings.get())

    @PutMapping
    @PreAuthorize(Permissions.HAS_SETTINGS_MANAGE)
    fun update(@Valid @RequestBody body: SettingsRequest): SettingsResponse {
        val edit = ClinicSettings.validated(
            body.name, body.legalName, body.address, body.phone, body.letterhead, body.receiptHeader, body.receiptFooter,
            body.workingHours, body.idleLockMinutes,
        )
        return SettingsResponse(settings.update(edit, body.version ?: throw ValidationException("version", "validation.not_null")))
    }

    @GetMapping("/logo")
    fun logo(): ResponseEntity<ByteArray> {
        val (bytes, type) = settings.logo()
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type))
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic()).body(bytes)
    }

    @PutMapping("/logo", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Permissions.HAS_SETTINGS_MANAGE)
    fun uploadLogo(@RequestPart("file") file: MultipartFile) {
        if (file.size > LogoImage.MAX_BYTES) throw ValidationException("file", "validation.image_size", mapOf("maxKb" to LogoImage.MAX_BYTES / 1024))
        settings.setLogo(file.bytes)
    }

    @DeleteMapping("/logo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Permissions.HAS_SETTINGS_MANAGE)
    fun removeLogo() = settings.removeLogo()
}
