package uz.medclinic.vendor.licensing.web

import jakarta.validation.Valid
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uz.medclinic.license.LicenseEnvelope
import uz.medclinic.license.LicenseModules
import uz.medclinic.vendor.licensing.application.LicenseService
import uz.medclinic.vendor.licensing.domain.License
import uz.medclinic.vendor.licensing.domain.LicenseStatus
import uz.medclinic.vendor.licensing.domain.LicenseTerms
import uz.medclinic.vendor.shared.security.Permissions
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class LicenseTermsRequest(
    @field:Size(max = 50) val plan: String?,
    @field:Size(max = 30) val modules: List<String>?,
    val maxUsers: Int?,
    val maxBranches: Int?,
    val startsOn: LocalDate?,
    val endsOn: LocalDate?,
    val expiryGraceDays: Int?,
    /** Required on renew. */
    @field:PositiveOrZero val version: Int? = null,
) {
    fun terms() = LicenseTerms.of(plan, modules, maxUsers, maxBranches, startsOn, endsOn, expiryGraceDays)
}

data class RevokeRequest(@field:Size(max = 500) val reason: String?, @field:PositiveOrZero val version: Int)

data class LicenseResponse(
    val id: UUID,
    val clinicId: UUID,
    val serial: Long,
    val plan: String,
    val modules: List<String>,
    val maxUsers: Int,
    val maxBranches: Int,
    val startsOn: LocalDate,
    val endsOn: LocalDate,
    val validFrom: Instant,
    val validUntil: Instant,
    val expiryGraceDays: Int,
    val graceEndsAt: Instant,
    val status: LicenseStatus,
    val issuedAt: Instant,
    val issuedBy: UUID,
    val keyId: String,
    val renewedFrom: UUID?,
    val supersededBy: UUID?,
    val revokedAt: Instant?,
    val revokeReason: String?,
    val version: Int,
) {
    constructor(l: License, now: Instant) : this(
        l.id, l.clinicId, l.serial, l.terms.plan, l.terms.modules, l.terms.maxUsers, l.terms.maxBranches, l.terms.startsOn,
        l.terms.endsOn, l.terms.validFrom, l.terms.validUntil, l.terms.expiryGraceDays, l.graceEndsAt(), l.status(now),
        l.issuedAt, l.issuedBy, l.keyId, l.renewedFrom, l.supersededBy, l.revokedAt, l.revokeReason, l.version,
    )
}

data class LicenseCatalogResponse(val modules: List<String>, val defaultExpiryGraceDays: Int, val maxExpiryGraceDays: Int)

@RestController
@RequestMapping("/api/v1")
class LicenseController(private val licenses: LicenseService, private val clock: Clock) {
    @GetMapping("/clinics/{clinicId}/licenses")
    @PreAuthorize(Permissions.HAS_LICENSE_VIEW)
    fun list(@PathVariable clinicId: UUID) = licenses.list(clinicId).map { LicenseResponse(it, clock.instant()) }

    @PostMapping("/clinics/{clinicId}/licenses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.HAS_LICENSE_MANAGE)
    fun issue(@PathVariable clinicId: UUID, @Valid @RequestBody body: LicenseTermsRequest) =
        LicenseResponse(licenses.issue(clinicId, body.terms()), clock.instant())

    @GetMapping("/licenses/{id}")
    @PreAuthorize(Permissions.HAS_LICENSE_VIEW)
    fun get(@PathVariable id: UUID) = LicenseResponse(licenses.get(id), clock.instant())

    @PostMapping("/licenses/{id}/renew")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.HAS_LICENSE_MANAGE)
    fun renew(@PathVariable id: UUID, @Valid @RequestBody body: LicenseTermsRequest): LicenseResponse {
        val version = body.version ?: throw uz.medclinic.vendor.shared.domain.ValidationException("version", "validation.not_null")
        return LicenseResponse(licenses.renew(id, version, body.terms()), clock.instant())
    }

    @PostMapping("/licenses/{id}/revoke")
    @PreAuthorize(Permissions.HAS_LICENSE_MANAGE)
    fun revoke(@PathVariable id: UUID, @Valid @RequestBody body: RevokeRequest) =
        LicenseResponse(licenses.revoke(id, body.version, body.reason), clock.instant())

    @GetMapping("/licenses/{id}/file")
    @PreAuthorize(Permissions.HAS_LICENSE_VIEW)
    fun download(@PathVariable id: UUID): ResponseEntity<ByteArray> {
        val file = licenses.download(id)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(LicenseEnvelope.MEDIA_TYPE))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName).build().toString())
            .body(file.bytes)
    }

    @GetMapping("/license-keys")
    @PreAuthorize(Permissions.HAS_LICENSE_VIEW)
    fun publicKeys() = licenses.publicKeys()

    @GetMapping("/license-catalog")
    @PreAuthorize(Permissions.HAS_LICENSE_VIEW)
    fun catalog() = LicenseCatalogResponse(LicenseModules.ALL, LicenseTerms.DEFAULT_GRACE_DAYS, uz.medclinic.license.LicensePayload.MAX_GRACE_DAYS)
}
