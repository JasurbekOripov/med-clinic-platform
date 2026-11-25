package uz.medclinic.clinic.vendorlink.application

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.clinic.audit.api.AuditEvent
import uz.medclinic.clinic.audit.api.AuditRecorder
import uz.medclinic.clinic.shared.LicenseLimits
import uz.medclinic.clinic.vendorlink.VendorLinkProperties
import uz.medclinic.clinic.vendorlink.domain.ClockGuard
import uz.medclinic.clinic.vendorlink.domain.LicenseStatus
import uz.medclinic.clinic.vendorlink.infrastructure.JdbcInstallationRepository
import uz.medclinic.license.LicensePayload
import uz.medclinic.license.LicenseVerification
import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.domain.ValidationException
import java.time.Clock
import java.time.Duration
import java.util.UUID

/**
 * Offline license enforcement. The installed file is verified when it is installed and when the server starts;
 * the time-based state is evaluated on every call (cheap, in memory).
 */
@Service
class LicenseService(
    private val repository: JdbcInstallationRepository,
    private val installation: InstallationService,
    private val keyring: Keyring,
    private val properties: VendorLinkProperties,
    private val audit: AuditRecorder,
    private val clock: Clock,
) : LicenseLimits {
    private val log = LoggerFactory.getLogger(javaClass)

    private data class Loaded(val payload: LicensePayload?, val invalid: Boolean, val revoked: Boolean)

    @Volatile
    private var loaded: Loaded? = null

    fun status(): LicenseStatus {
        val now = clock.instant()
        val current = loaded ?: load().also { loaded = it }
        val link = repository.state()
        val rolledBack = ClockGuard.rolledBack(now, link.maxSeenTime, properties.clockRollbackTolerance)
        val skewWarning = link.lastServerTime != null && link.lastSuccessAt != null &&
            Duration.between(link.lastServerTime, link.lastSuccessAt).abs() > properties.clockSkewWarning
        return LicenseStatus.evaluate(current.payload, current.invalid, current.revoked, rolledBack, skewWarning, now)
    }

    override fun maxUsers(): Int? = status().takeIf { it.usable }?.payload?.maxUsers

    override fun hasModule(module: String): Boolean = status().let { it.usable && it.payload?.hasModule(module) == true }

    fun installedLicenseId(): UUID? = repository.findLicense()?.licenseId

    /** Verifies and installs a license file (from pairing, heartbeat sync or a manual upload). */
    @Transactional
    fun install(file: ByteArray, source: String): LicensePayload {
        val identity = installation.current()
        val verifier = keyring.verifier() ?: throw DomainException("license.no_trusted_keys")
        val payload = when (val result = verifier.verify(file, expectedClinicId = identity.clinicId)) {
            is LicenseVerification.Valid -> result.payload
            is LicenseVerification.Invalid -> throw ValidationException("file", "validation.license_${result.reason.name.lowercase()}")
        }
        val current = repository.findLicense()
        if (current != null && current.licenseId != payload.licenseId && current.revokedAt == null) {
            val currentSerial = parse(current.file)?.serial ?: 0
            // Never replace a newer license with an older file (e.g. an old .lic uploaded by mistake).
            if (payload.serial < currentSerial) throw ValidationException("file", "validation.license_older")
        }
        repository.saveLicense(identity.clinicId, identity.branchId, payload.licenseId, file, clock.instant())
        loaded = null
        audit.record(
            AuditEvent(
                "license.installed", "license", payload.licenseId,
                after = mapOf("serial" to payload.serial, "validUntil" to payload.validUntil, "maxUsers" to payload.maxUsers, "source" to source),
            ),
        )
        return payload
    }

    @Transactional
    fun markRevoked(licenseId: UUID) {
        if (repository.markLicenseRevoked(licenseId, clock.instant())) {
            loaded = null
            audit.record(AuditEvent("license.revoked_by_vendor", "license", licenseId))
            log.warn("The vendor revoked the installed license {}", licenseId)
        }
    }

    private fun load(): Loaded {
        val installed = repository.findLicense() ?: return Loaded(null, invalid = false, revoked = false)
        val identity = installation.currentOrNull() ?: return Loaded(null, invalid = true, revoked = false)
        val verifier = keyring.verifier() ?: return Loaded(null, invalid = true, revoked = false)
        return when (val result = verifier.verify(installed.file, expectedClinicId = identity.clinicId)) {
            is LicenseVerification.Valid -> Loaded(result.payload, invalid = false, revoked = installed.revokedAt != null)
            is LicenseVerification.Invalid -> {
                log.error("Installed license does not verify: {}", result.reason)
                Loaded(null, invalid = true, revoked = false)
            }
        }
    }

    private fun parse(file: ByteArray): LicensePayload? =
        (keyring.verifier()?.verify(file) as? LicenseVerification.Valid)?.payload

    /** Test/support hook: forget the cached license after direct database changes. */
    fun reload() {
        loaded = null
    }
}
