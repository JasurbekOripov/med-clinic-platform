package uz.medclinic.vendor.monitoring.application

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.vendor.devices.api.DeviceQueries
import uz.medclinic.vendor.monitoring.api.AlertType
import uz.medclinic.vendor.monitoring.domain.HealthRules
import uz.medclinic.vendor.monitoring.domain.ServerStatus
import uz.medclinic.vendor.monitoring.domain.ServerStatusRule
import uz.medclinic.vendor.monitoring.infrastructure.JdbcMonitoringRepository
import uz.medclinic.vendor.tenancy.api.TenancyQueries
import java.time.Clock

/**
 * Periodic, idempotent alert reconciliation: detects silent servers and time-based conditions (stale backups),
 * and closes alerts of servers that were revoked or whose branch/clinic is no longer active.
 * Safe to run concurrently with heartbeats thanks to the unique open-alert index.
 */
@Component
class AlertEvaluator(
    private val devices: DeviceQueries,
    private val tenancy: TenancyQueries,
    private val repository: JdbcMonitoringRepository,
    private val alerts: AlertService,
    private val properties: MonitoringProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${vendor.monitoring.evaluation-interval:60s}", initialDelayString = "\${vendor.monitoring.evaluation-interval:60s}")
    fun scheduled() {
        if (!properties.evaluationEnabled) return
        try {
            evaluate()
        } catch (ex: Exception) {
            log.error("Alert evaluation failed", ex)
        }
    }

    @Transactional
    fun evaluate() {
        val now = clock.instant()
        val active = devices.activeDevices()
        val branches = tenancy.findBranches(active.map { it.branchId })
        val health = repository.findHealth(active.map { it.id })
        val monitored = mutableSetOf<java.util.UUID>()

        active.forEach { device ->
            if (branches[device.branchId]?.operational != true) return@forEach
            monitored += device.id
            val target = AlertService.Target(device.id, device.clinicId, device.branchId)
            val snapshot = health[device.id]
            // A paired server that never reported is as worrying as one that went silent.
            val lastSignal = snapshot?.lastHeartbeatAt ?: device.pairedAt
            if (ServerStatusRule.of(true, lastSignal, properties.offlineAfter, now) == ServerStatus.OFFLINE) {
                alerts.open(target, AlertType.SERVER_OFFLINE, mapOf("lastHeartbeatAt" to snapshot?.lastHeartbeatAt, "pairedAt" to device.pairedAt), now)
            }
            if (snapshot != null) {
                alerts.reconcile(target, HealthRules.HEALTH_TYPES, HealthRules.evaluate(snapshot, properties.thresholds, now), now)
            }
        }
        repository.openAlertDeviceIds().filterNot(monitored::contains).forEach { alerts.resolve(it, null, now) }
    }

    @Scheduled(cron = "0 17 3 * * *")
    @Transactional
    fun purgeOldHeartbeats() {
        val removed = repository.purgeHeartbeatsBefore(clock.instant().minus(properties.heartbeatRetention))
        if (removed > 0) log.info("Purged {} heartbeats older than {}", removed, properties.heartbeatRetention)
    }
}
