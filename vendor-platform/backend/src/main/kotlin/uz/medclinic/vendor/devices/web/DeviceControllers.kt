package uz.medclinic.vendor.devices.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uz.medclinic.license.LicenseEnvelope
import uz.medclinic.vendor.devices.api.AuthenticatedDevice
import uz.medclinic.vendor.devices.application.ClaimThrottle
import uz.medclinic.vendor.devices.application.PairingService
import uz.medclinic.vendor.devices.domain.Device
import uz.medclinic.vendor.devices.domain.DeviceStatus
import uz.medclinic.vendor.devices.domain.PairingCode
import uz.medclinic.vendor.devices.domain.PairingCodeState
import uz.medclinic.platform.domain.DomainException
import uz.medclinic.vendor.shared.security.Permissions
import uz.medclinic.platform.web.ClientInfo
import uz.medclinic.vendorprotocol.DeviceApi
import uz.medclinic.vendorprotocol.PairingClaimRequest
import uz.medclinic.vendorprotocol.PairingClaimResponse
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class PairingCodeResponse(
    val id: UUID,
    val branchId: UUID,
    val state: PairingCodeState,
    val expiresAt: Instant,
    val createdAt: Instant,
    val claimedAt: Instant?,
    val claimedDeviceId: UUID?,
    /** Only present in the response that created the code. */
    val code: String? = null,
) {
    constructor(c: PairingCode, now: Instant, code: String? = null) :
        this(c.id, c.branchId, c.state(now), c.expiresAt, c.createdAt, c.claimedAt, c.claimedDeviceId, code)
}

data class DeviceResponse(
    val id: UUID,
    val branchId: UUID,
    val status: DeviceStatus,
    val hostname: String,
    val os: String,
    val appVersion: String,
    val pairedAt: Instant,
    val lastSeenAt: Instant?,
    val lastIp: String?,
    val revokedAt: Instant?,
    val revokeReason: String?,
    val version: Int,
) {
    constructor(d: Device) : this(d.id, d.branchId, d.status, d.hostname, d.os, d.appVersion, d.pairedAt, d.lastSeenAt, d.lastIp, d.revokedAt, d.revokeReason, d.version)
}

data class RevokeDeviceRequest(@field:Size(max = 200) val reason: String?, @field:PositiveOrZero val version: Int)

@RestController
@RequestMapping("/api/v1")
class DeviceAdminController(private val pairing: PairingService, private val clock: Clock) {
    @PostMapping("/branches/{branchId}/pairing-codes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.HAS_DEVICE_PAIR)
    fun createCode(@PathVariable branchId: UUID): PairingCodeResponse {
        val created = pairing.createCode(branchId)
        return PairingCodeResponse(created.code, clock.instant(), created.plainCode)
    }

    @GetMapping("/branches/{branchId}/pairing-codes")
    @PreAuthorize(Permissions.HAS_DEVICE_PAIR)
    fun listCodes(@PathVariable branchId: UUID) = pairing.listCodes(branchId).map { PairingCodeResponse(it, clock.instant()) }

    @PostMapping("/pairing-codes/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Permissions.HAS_DEVICE_PAIR)
    fun cancelCode(@PathVariable id: UUID) = pairing.cancelCode(id)

    @GetMapping("/branches/{branchId}/devices")
    @PreAuthorize(Permissions.HAS_MONITORING_VIEW)
    fun devices(@PathVariable branchId: UUID) = pairing.listDevices(branchId).map(::DeviceResponse)

    @PostMapping("/devices/{id}/revoke")
    @PreAuthorize(Permissions.HAS_DEVICE_REVOKE)
    fun revoke(@PathVariable id: UUID, @Valid @RequestBody body: RevokeDeviceRequest) =
        DeviceResponse(pairing.revokeDevice(id, body.version, body.reason))
}

@RestController
class DeviceApiController(private val pairing: PairingService, private val throttle: ClaimThrottle) {
    @PostMapping(DeviceApi.PAIRING_CLAIM_PATH)
    fun claim(@RequestBody body: PairingClaimRequest, request: HttpServletRequest): PairingClaimResponse {
        val ip = ClientInfo.of(request).ip
        throttle.checkAllowed(ip)
        try {
            return pairing.claim(body, ip)
        } catch (ex: DomainException) {
            if (ex.code == "pairing.invalid_code") throttle.recordFailure(ip)
            throw ex
        }
    }

    @GetMapping(DeviceApi.LICENSE_PATH)
    fun license(@AuthenticationPrincipal device: AuthenticatedDevice): ResponseEntity<ByteArray> =
        ResponseEntity.ok().contentType(MediaType.parseMediaType(LicenseEnvelope.MEDIA_TYPE)).body(pairing.currentLicenseFile(device))
}
