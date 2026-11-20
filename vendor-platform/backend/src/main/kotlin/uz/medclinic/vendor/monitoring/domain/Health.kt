package uz.medclinic.vendor.monitoring.domain

import uz.medclinic.vendor.monitoring.api.AlertType
import uz.medclinic.vendorprotocol.BackupStatus
import uz.medclinic.vendorprotocol.DiskUsage
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class DeviceHealth(
    val deviceId: UUID,
    val clinicId: UUID,
    val branchId: UUID,
    val lastHeartbeatAt: Instant,
    val appVersion: String,
    val uptimeSeconds: Long,
    val disks: List<DiskUsage>,
    val lastBackupAt: Instant?,
    val lastBackupStatus: BackupStatus?,
    val lastBackupSuccessAt: Instant?,
    val lastBackupSizeBytes: Long?,
    val errorCount: Long?,
    val warningCount: Long?,
    val clockSkewSeconds: Long,
    val installedLicenseId: UUID?,
)

data class HealthThresholds(
    val diskMinFreeRatio: Double = 0.10,
    val diskMinFreeBytes: Long = 5L * 1024 * 1024 * 1024,
    val backupStaleAfter: Duration = Duration.ofHours(36),
    val maxClockSkew: Duration = Duration.ofMinutes(5),
)

data class AlertCondition(val type: AlertType, val details: Map<String, Any?>)

/** Pure alert rules on a health snapshot (everything except SERVER_OFFLINE, which depends on silence). */
object HealthRules {
    val HEALTH_TYPES = setOf(AlertType.DISK_LOW, AlertType.BACKUP_FAILED, AlertType.BACKUP_STALE, AlertType.CLOCK_SKEW)

    fun evaluate(health: DeviceHealth, thresholds: HealthThresholds, now: Instant): List<AlertCondition> = buildList {
        val lowDisks = health.disks.filter { it.totalBytes > 0 && isLow(it, thresholds) }
        if (lowDisks.isNotEmpty()) {
            add(AlertCondition(AlertType.DISK_LOW, mapOf("disks" to lowDisks.map { mapOf("mount" to it.mount, "freeBytes" to it.freeBytes, "totalBytes" to it.totalBytes) })))
        }
        if (health.lastBackupStatus == BackupStatus.FAILED) {
            add(AlertCondition(AlertType.BACKUP_FAILED, mapOf("lastBackupAt" to health.lastBackupAt)))
        }
        // No backup information at all means backups are not set up yet: not an alert in V1.
        val lastSuccess = health.lastBackupSuccessAt
        if (lastSuccess != null && lastSuccess.plus(thresholds.backupStaleAfter).isBefore(now)) {
            add(AlertCondition(AlertType.BACKUP_STALE, mapOf("lastBackupSuccessAt" to lastSuccess)))
        }
        if (Math.abs(health.clockSkewSeconds) > thresholds.maxClockSkew.toSeconds()) {
            add(AlertCondition(AlertType.CLOCK_SKEW, mapOf("clockSkewSeconds" to health.clockSkewSeconds)))
        }
    }

    private fun isLow(disk: DiskUsage, t: HealthThresholds): Boolean =
        disk.freeBytes < t.diskMinFreeBytes || disk.freeBytes.toDouble() / disk.totalBytes < t.diskMinFreeRatio
}

enum class ServerStatus { ONLINE, OFFLINE, NEVER_SEEN, NOT_PAIRED }

object ServerStatusRule {
    fun of(paired: Boolean, lastHeartbeatAt: Instant?, offlineAfter: Duration, now: Instant): ServerStatus = when {
        !paired -> ServerStatus.NOT_PAIRED
        lastHeartbeatAt == null -> ServerStatus.NEVER_SEEN
        lastHeartbeatAt.plus(offlineAfter).isBefore(now) -> ServerStatus.OFFLINE
        else -> ServerStatus.ONLINE
    }
}

data class Alert(
    val id: UUID,
    val deviceId: UUID,
    val clinicId: UUID,
    val branchId: UUID,
    val type: AlertType,
    val details: String?,
    val openedAt: Instant,
    val resolvedAt: Instant?,
)
