package uz.medclinic.vendor.monitoring.api

import java.time.Instant
import java.util.UUID

enum class AlertType { SERVER_OFFLINE, DISK_LOW, BACKUP_FAILED, BACKUP_STALE, CLOCK_SKEW }

/**
 * Published after an alert opens/resolves. Extension point for the future notifications module
 * (Telegram, e-mail); V1 has no listeners.
 */
data class AlertOpened(val alertId: UUID, val type: AlertType, val deviceId: UUID, val clinicId: UUID, val branchId: UUID, val openedAt: Instant)

data class AlertResolved(val alertId: UUID, val type: AlertType, val deviceId: UUID, val clinicId: UUID, val branchId: UUID, val resolvedAt: Instant)
