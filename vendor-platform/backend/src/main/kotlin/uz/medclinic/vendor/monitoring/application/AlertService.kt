package uz.medclinic.vendor.monitoring.application

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import uz.medclinic.vendor.monitoring.api.AlertOpened
import uz.medclinic.vendor.monitoring.api.AlertResolved
import uz.medclinic.vendor.monitoring.api.AlertType
import uz.medclinic.vendor.monitoring.domain.AlertCondition
import uz.medclinic.vendor.monitoring.infrastructure.JdbcMonitoringRepository
import java.time.Instant
import java.util.UUID

/** Opens/resolves alerts idempotently and publishes the corresponding events. Callers own the transaction. */
@Component
class AlertService(private val repository: JdbcMonitoringRepository, private val events: ApplicationEventPublisher) {
    data class Target(val deviceId: UUID, val clinicId: UUID, val branchId: UUID)

    /** Makes the open alerts of [types] match exactly [conditions]. */
    fun reconcile(target: Target, types: Set<AlertType>, conditions: List<AlertCondition>, now: Instant) {
        conditions.filter { it.type in types }.forEach { open(target, it.type, it.details, now) }
        resolve(target.deviceId, types - conditions.map { it.type }.toSet(), now)
    }

    fun open(target: Target, type: AlertType, details: Map<String, Any?>, now: Instant) {
        repository.openAlert(target.deviceId, target.clinicId, target.branchId, type, details, now)?.let {
            events.publishEvent(AlertOpened(it.id, type, it.deviceId, it.clinicId, it.branchId, now))
        }
    }

    fun resolve(deviceId: UUID, types: Collection<AlertType>?, now: Instant) {
        if (types != null && types.isEmpty()) return
        repository.resolveAlerts(deviceId, types, now).forEach {
            events.publishEvent(AlertResolved(it.id, it.type, it.deviceId, it.clinicId, it.branchId, now))
        }
    }
}
