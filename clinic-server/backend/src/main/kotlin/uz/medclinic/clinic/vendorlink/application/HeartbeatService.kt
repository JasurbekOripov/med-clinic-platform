package uz.medclinic.clinic.vendorlink.application

import org.slf4j.LoggerFactory
import org.springframework.boot.info.BuildProperties
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import uz.medclinic.clinic.vendorlink.VendorLinkProperties
import uz.medclinic.clinic.vendorlink.domain.ClockGuard
import uz.medclinic.clinic.vendorlink.infrastructure.ErrorCounter
import uz.medclinic.clinic.vendorlink.infrastructure.JdbcInstallationRepository
import uz.medclinic.clinic.vendorlink.infrastructure.TokenCipher
import uz.medclinic.clinic.vendorlink.infrastructure.VendorClient
import uz.medclinic.common.Ids
import uz.medclinic.platform.domain.DomainException
import uz.medclinic.vendorprotocol.DiskUsage
import uz.medclinic.vendorprotocol.ErrorCounters
import uz.medclinic.vendorprotocol.HeartbeatRequest
import uz.medclinic.vendorprotocol.HeartbeatResponse
import uz.medclinic.vendorprotocol.InstalledLicenseStatus
import java.io.File
import java.lang.management.ManagementFactory
import java.time.Clock
import java.time.Duration

/**
 * Every minute: tell the vendor this server is alive (technical numbers only, never patient data) and keep the
 * license in sync: a renewal is downloaded and installed, a revocation is applied. Offline is normal: the clinic
 * keeps working and the next successful heartbeat catches up.
 */
@Service
class HeartbeatService(
    private val repository: JdbcInstallationRepository,
    private val installation: InstallationService,
    private val licenses: LicenseService,
    private val client: VendorClient,
    private val cipher: TokenCipher,
    private val errors: ErrorCounter,
    private val properties: VendorLinkProperties,
    private val build: BuildProperties?,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${clinic.vendor.heartbeat-interval:60s}", initialDelayString = "\${clinic.vendor.heartbeat-initial-delay:15s}")
    fun scheduled() {
        runCatching { beat() }.onFailure { log.debug("Heartbeat failed", it) }
    }

    /** One heartbeat; returns true when the vendor answered. */
    fun beat(): Boolean {
        val current = installation.installation() ?: return false
        val now = clock.instant()
        if (!ClockGuard.rolledBack(now, repository.state().maxSeenTime, properties.clockRollbackTolerance)) repository.observeTime(now)
        val token = cipher.decrypt(current.deviceTokenEnc)
        val counters = errors.takeSnapshot()
        val request = HeartbeatRequest(
            heartbeatId = Ids.newId(),
            sentAt = now,
            appVersion = build?.version ?: "dev",
            uptimeSeconds = ManagementFactory.getRuntimeMXBean().uptime / 1000,
            disks = disks(),
            lastBackup = null, // backups arrive at milestone M9
            errors = ErrorCounters(counters.since, counters.errors, counters.warnings),
            installedLicenseId = licenses.installedLicenseId(),
        )
        val response = try {
            client.heartbeat(current.vendorUrl, token, request)
        } catch (ex: DomainException) {
            repository.recordAttempt(clock.instant(), success = false, errorCode = ex.code, serverTime = null)
            return false
        }
        repository.recordAttempt(clock.instant(), success = true, errorCode = null, serverTime = response.serverTime)
        if (Duration.between(response.serverTime, clock.instant()).abs() <= properties.clockRollbackTolerance) {
            repository.trustTime(clock.instant())
        }
        syncLicense(current.vendorUrl, token, request.installedLicenseId, response)
        return true
    }

    private fun syncLicense(vendorUrl: String, token: String, installedId: java.util.UUID?, response: HeartbeatResponse) {
        when (response.license.installedLicenseStatus) {
            InstalledLicenseStatus.REVOKED -> installedId?.let(licenses::markRevoked)
            InstalledLicenseStatus.SUPERSEDED, InstalledLicenseStatus.NONE, InstalledLicenseStatus.UNKNOWN -> {
                if (response.license.currentLicenseId != null && response.license.currentLicenseId != installedId) {
                    runCatching { licenses.install(client.license(vendorUrl, token), "heartbeat") }
                        .onFailure { log.error("Could not install the license offered by the vendor", it) }
                }
            }
            InstalledLicenseStatus.CURRENT -> Unit
        }
    }

    private fun disks(): List<DiskUsage> = File.listRoots()
        .filter { it.totalSpace > 0 }
        .take(MAX_DISKS)
        .map { DiskUsage(it.absolutePath.take(64), it.totalSpace, it.usableSpace.coerceAtMost(it.totalSpace)) }

    private companion object {
        const val MAX_DISKS = 26
    }
}
