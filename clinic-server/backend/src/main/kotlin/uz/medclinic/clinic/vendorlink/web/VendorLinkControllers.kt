package uz.medclinic.clinic.vendorlink.web

import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import uz.medclinic.clinic.shared.Permissions
import uz.medclinic.clinic.vendorlink.application.HeartbeatService
import uz.medclinic.clinic.vendorlink.application.InstallationService
import uz.medclinic.clinic.vendorlink.application.LicenseService
import uz.medclinic.clinic.vendorlink.application.SetupService
import uz.medclinic.clinic.vendorlink.application.SetupStatus
import uz.medclinic.clinic.vendorlink.domain.LicenseState
import uz.medclinic.clinic.vendorlink.infrastructure.JdbcInstallationRepository
import uz.medclinic.license.LicenseVerifier
import uz.medclinic.platform.domain.ValidationException
import java.time.Instant
import java.util.UUID

data class SetupStatusResponse(val paired: Boolean, val hasAdmin: Boolean, val completed: Boolean, val clinicCode: String?, val branchCode: String?) {
    constructor(s: SetupStatus) : this(s.paired, s.hasAdmin, s.completed, s.clinicCode, s.branchCode)
}

data class PairRequest(@field:Size(max = 300) val vendorUrl: String?, @field:Size(max = 40) val code: String?)

data class FirstAdminRequest(
    @field:Size(max = 200) val fullName: String?,
    @field:Size(max = 50) val login: String?,
    @field:Size(max = 200) val password: String?,
    @field:Size(max = 200) val vendorSupportPassword: String?,
)

/** Public: shown on the stop screen and as a banner, so it contains no secrets. */
data class LicenseStatusResponse(
    val state: LicenseState,
    val usable: Boolean,
    val clinicName: String?,
    val plan: String?,
    val validUntil: Instant?,
    val graceEndsAt: Instant?,
    val maxUsers: Int?,
    val modules: List<String>,
    val clockWarning: Boolean,
)

data class VendorLinkResponse(
    val vendorUrl: String?,
    val deviceId: UUID?,
    val clinicCode: String?,
    val branchCode: String?,
    val pairedAt: Instant?,
    val lastAttemptAt: Instant?,
    val lastSuccessAt: Instant?,
    val lastErrorCode: String?,
    val lastServerTime: Instant?,
    val licenseId: UUID?,
    val licenseSerial: Long?,
)

@RestController
@RequestMapping("/api/v1/setup")
class SetupController(private val setup: SetupService) {
    @GetMapping("/status")
    fun status() = SetupStatusResponse(setup.status())

    @PostMapping("/pair")
    fun pair(@Valid @RequestBody body: PairRequest) = SetupStatusResponse(setup.pair(body.vendorUrl, body.code))

    @PostMapping("/admin")
    @ResponseStatus(HttpStatus.CREATED)
    fun admin(@Valid @RequestBody body: FirstAdminRequest): SetupStatusResponse {
        setup.createAdmin(body.fullName, body.login, body.password, body.vendorSupportPassword)
        return SetupStatusResponse(setup.status())
    }
}

@RestController
@RequestMapping("/api/v1/license")
class LicenseController(
    private val licenses: LicenseService,
    private val heartbeat: HeartbeatService,
    private val installation: InstallationService,
    private val repository: JdbcInstallationRepository,
) {
    @GetMapping("/status")
    fun status(): LicenseStatusResponse {
        val s = licenses.status()
        return LicenseStatusResponse(
            s.state, s.usable, s.payload?.clinicName, s.payload?.plan, s.payload?.validUntil, s.graceEndsAt, s.payload?.maxUsers,
            s.payload?.modules.orEmpty(), s.clockWarning,
        )
    }

    /** Offline fallback: the Admin uploads the `.lic` file received from the vendor. */
    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @PreAuthorize(Permissions.HAS_LICENSE_MANAGE)
    fun upload(@RequestPart("file") file: MultipartFile): LicenseStatusResponse {
        if (file.size > LicenseVerifier.MAX_FILE_BYTES) throw ValidationException("file", "validation.license_too_large")
        licenses.install(file.bytes, "upload")
        return status()
    }

    /** "Check now": one heartbeat immediately (e.g. right after the vendor renewed the license). */
    @PostMapping("/sync")
    @PreAuthorize(Permissions.HAS_LICENSE_MANAGE)
    fun sync(): LicenseStatusResponse {
        heartbeat.beat()
        return status()
    }

    @GetMapping("/vendor-link")
    @PreAuthorize(Permissions.HAS_LICENSE_MANAGE)
    fun vendorLink(): VendorLinkResponse {
        val i = installation.installation()
        val state = repository.state()
        val serial = licenses.status().payload?.serial
        return VendorLinkResponse(
            i?.vendorUrl, i?.deviceId, i?.clinicCode, i?.branchCode, i?.pairedAt, state.lastAttemptAt, state.lastSuccessAt,
            state.lastErrorCode, state.lastServerTime, licenses.installedLicenseId(), serial,
        )
    }
}
