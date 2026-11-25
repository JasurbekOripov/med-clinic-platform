package uz.medclinic.vendor.monitoring.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.vendor.devices.api.AuthenticatedDevice
import uz.medclinic.vendor.devices.api.DeviceQueries
import uz.medclinic.vendor.licensing.api.LicenseQueries
import uz.medclinic.vendor.licensing.api.LicenseRecordState
import uz.medclinic.vendor.monitoring.api.AlertType
import uz.medclinic.vendor.monitoring.domain.DeviceHealth
import uz.medclinic.vendor.monitoring.domain.HealthRules
import uz.medclinic.vendor.monitoring.infrastructure.JdbcMonitoringRepository
import uz.medclinic.platform.domain.FieldViolation
import uz.medclinic.platform.domain.Text
import uz.medclinic.platform.domain.ValidationException
import uz.medclinic.vendorprotocol.BackupStatus
import uz.medclinic.vendorprotocol.HeartbeatRequest
import uz.medclinic.vendorprotocol.HeartbeatResponse
import uz.medclinic.vendorprotocol.InstalledLicenseStatus
import uz.medclinic.vendorprotocol.LicenseSyncInfo
import java.time.Clock
import java.time.Duration

@Service
class HeartbeatService(
    private val repository: JdbcMonitoringRepository,
    private val alerts: AlertService,
    private val licenses: LicenseQueries,
    private val properties: MonitoringProperties,
    private val clock: Clock,
    private val devices: DeviceQueries,
) {
    /** Idempotent by `heartbeatId`: a retried heartbeat is acknowledged without being applied twice. */
    @Transactional
    fun ingest(device: AuthenticatedDevice, request: HeartbeatRequest): HeartbeatResponse {
        validate(request)
        val now = clock.instant()
        val fresh = repository.insertHeartbeat(request.heartbeatId, device.deviceId, now, request.sentAt, request)
        if (fresh) {
            val backup = request.lastBackup
            val health = DeviceHealth(
                deviceId = device.deviceId,
                clinicId = device.clinicId,
                branchId = device.branchId,
                lastHeartbeatAt = now,
                appVersion = Text.clean(request.appVersion)!!,
                uptimeSeconds = request.uptimeSeconds,
                disks = request.disks,
                lastBackupAt = backup?.completedAt,
                lastBackupStatus = backup?.status,
                lastBackupSuccessAt = backup?.lastSuccessAt ?: backup?.completedAt?.takeIf { backup.status == BackupStatus.SUCCESS },
                lastBackupSizeBytes = backup?.sizeBytes,
                errorCount = request.errors?.errorCount,
                warningCount = request.errors?.warningCount,
                clockSkewSeconds = Duration.between(request.sentAt, now).toSeconds(),
                installedLicenseId = request.installedLicenseId,
            )
            repository.upsertHealth(health)
            val target = AlertService.Target(device.deviceId, device.clinicId, device.branchId)
            alerts.resolve(device.deviceId, setOf(AlertType.SERVER_OFFLINE), now)
            alerts.reconcile(target, HealthRules.HEALTH_TYPES, HealthRules.evaluate(health, properties.thresholds, now), now)
        }
        return HeartbeatResponse(now, devices.heartbeatIntervalSeconds(), licenseSync(device, request))
    }

    private fun licenseSync(device: AuthenticatedDevice, request: HeartbeatRequest): LicenseSyncInfo {
        val currentId = licenses.currentLicense(device.clinicId)?.id
        val installed = request.installedLicenseId ?: return LicenseSyncInfo(currentId, InstalledLicenseStatus.NONE)
        val status = when (licenses.stateOf(device.clinicId, installed)) {
            LicenseRecordState.CURRENT -> InstalledLicenseStatus.CURRENT
            LicenseRecordState.SUPERSEDED -> InstalledLicenseStatus.SUPERSEDED
            LicenseRecordState.REVOKED -> InstalledLicenseStatus.REVOKED
            LicenseRecordState.UNKNOWN -> InstalledLicenseStatus.UNKNOWN
        }
        return LicenseSyncInfo(currentId, status)
    }

    private fun validate(r: HeartbeatRequest) {
        val errors = mutableListOf<FieldViolation>()
        val version = Text.clean(r.appVersion)
        if (version == null || version.length > 50) errors += FieldViolation("appVersion", "validation.size", mapOf("min" to 1, "max" to 50))
        if (r.uptimeSeconds < 0) errors += FieldViolation("uptimeSeconds", "validation.positive_or_zero")
        if (r.disks.size > MAX_DISKS) errors += FieldViolation("disks", "validation.size", mapOf("max" to MAX_DISKS))
        r.disks.forEachIndexed { i, d ->
            if (d.mount.isBlank() || d.mount.length > 64 || d.totalBytes < 0 || d.freeBytes < 0 || d.freeBytes > d.totalBytes) {
                errors += FieldViolation("disks[$i]", "validation.invalid")
            }
        }
        r.errors?.let { if (it.errorCount < 0 || it.warningCount < 0) errors += FieldViolation("errors", "validation.invalid") }
        r.lastBackup?.sizeBytes?.let { if (it < 0) errors += FieldViolation("lastBackup.sizeBytes", "validation.invalid") }
        if (errors.isNotEmpty()) throw ValidationException(errors)
    }

    private companion object {
        const val MAX_DISKS = 26
    }
}
