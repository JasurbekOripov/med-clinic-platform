package uz.medclinic.vendor.monitoring.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.vendor.devices.api.DeviceQueries
import uz.medclinic.vendor.monitoring.api.AlertType
import uz.medclinic.vendor.monitoring.domain.Alert
import uz.medclinic.vendor.monitoring.domain.DeviceHealth
import uz.medclinic.vendor.monitoring.domain.ServerStatus
import uz.medclinic.vendor.monitoring.domain.ServerStatusRule
import uz.medclinic.vendor.monitoring.infrastructure.JdbcMonitoringRepository
import uz.medclinic.vendor.shared.domain.Page
import uz.medclinic.vendor.shared.domain.PageQuery
import uz.medclinic.vendor.tenancy.api.BranchSummary
import uz.medclinic.vendor.tenancy.api.TenancyQueries
import uz.medclinic.vendor.tenancy.api.TenantStatus
import java.time.Clock
import java.util.UUID

/** One row per live branch: its active Clinic Server (if any), latest health and open alerts. */
data class ServerRow(
    val branch: BranchSummary,
    val deviceId: UUID?,
    val hostname: String?,
    val status: ServerStatus,
    val health: DeviceHealth?,
    val openAlerts: List<AlertType>,
) {
    val tenantStatus: TenantStatus get() = branch.effectiveStatus
}

data class ServerOverview(val rows: List<ServerRow>, val counts: Map<ServerStatus, Int>, val openAlertCount: Int)

data class AlertView(val alert: Alert, val branch: BranchSummary?)

@Service
class MonitoringQueryService(
    private val tenancy: TenancyQueries,
    private val devices: DeviceQueries,
    private val repository: JdbcMonitoringRepository,
    private val properties: MonitoringProperties,
    private val clock: Clock,
) {
    /**
     * Built in memory: a vendor has at most a few thousand branches. Move to a SQL view with paging if that
     * assumption ever breaks.
     */
    @Transactional(readOnly = true)
    fun overview(statusFilter: ServerStatus?, query: String?): ServerOverview {
        val now = clock.instant()
        val branches = tenancy.listLiveBranches()
        val deviceByBranch = devices.activeDevices().associateBy { it.branchId }
        val health = repository.findHealth(deviceByBranch.values.map { it.id })
        val alertsByDevice = repository.openAlerts().groupBy { it.deviceId }

        val all = branches.map { branch ->
            val device = deviceByBranch[branch.id]
            val snapshot = device?.let { health[it.id] }
            ServerRow(
                branch = branch,
                deviceId = device?.id,
                hostname = device?.hostname,
                status = ServerStatusRule.of(device != null, snapshot?.lastHeartbeatAt, properties.offlineAfter, now),
                health = snapshot,
                openAlerts = device?.let { d -> alertsByDevice[d.id].orEmpty().map { it.type } }.orEmpty(),
            )
        }
        val needle = query?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val rows = all
            .filter { statusFilter == null || it.status == statusFilter }
            .filter { needle == null || listOf(it.branch.code, it.branch.name, it.branch.clinic.code, it.branch.clinic.name, it.hostname.orEmpty()).any { f -> f.lowercase().contains(needle) } }
            .sortedWith(compareBy<ServerRow>({ severity(it) }, { it.branch.code }))
        return ServerOverview(rows, ServerStatus.entries.associateWith { s -> all.count { it.status == s } }, all.sumOf { it.openAlerts.size })
    }

    @Transactional(readOnly = true)
    fun alerts(openOnly: Boolean, page: PageQuery): Page<AlertView> {
        val result = repository.alerts(openOnly, page)
        val branches = tenancy.findBranches(result.items.map { it.branchId }.toSet())
        return result.map { AlertView(it, branches[it.branchId]) }
    }

    /** Problems first: offline, then alerts, then never seen, online, not paired. */
    private fun severity(row: ServerRow): Int = when {
        row.status == ServerStatus.OFFLINE -> 0
        row.openAlerts.isNotEmpty() -> 1
        row.status == ServerStatus.NEVER_SEEN -> 2
        row.status == ServerStatus.ONLINE -> 3
        else -> 4
    }
}
