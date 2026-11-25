package uz.medclinic.vendor.monitoring.web

import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import uz.medclinic.vendor.devices.api.AuthenticatedDevice
import uz.medclinic.vendor.monitoring.api.AlertType
import uz.medclinic.vendor.monitoring.application.AlertView
import uz.medclinic.vendor.monitoring.application.HeartbeatService
import uz.medclinic.vendor.monitoring.application.MonitoringQueryService
import uz.medclinic.vendor.monitoring.application.ServerRow
import uz.medclinic.vendor.monitoring.domain.ServerStatus
import uz.medclinic.platform.domain.PageQuery
import uz.medclinic.vendor.shared.security.Permissions
import uz.medclinic.platform.web.PageResponse
import uz.medclinic.vendor.tenancy.api.TenantStatus
import uz.medclinic.vendorprotocol.BackupStatus
import uz.medclinic.vendorprotocol.DeviceApi
import uz.medclinic.vendorprotocol.DiskUsage
import uz.medclinic.vendorprotocol.HeartbeatRequest
import uz.medclinic.vendorprotocol.HeartbeatResponse
import java.time.Instant
import java.util.UUID

data class ServerRowResponse(
    val clinicId: UUID,
    val clinicCode: String,
    val clinicName: String,
    val branchId: UUID,
    val branchCode: String,
    val branchName: String,
    val tenantStatus: TenantStatus,
    val deviceId: UUID?,
    val hostname: String?,
    val status: ServerStatus,
    val lastHeartbeatAt: Instant?,
    val appVersion: String?,
    val uptimeSeconds: Long?,
    val disks: List<DiskUsage>,
    val lastBackupAt: Instant?,
    val lastBackupStatus: BackupStatus?,
    val lastBackupSuccessAt: Instant?,
    val errorCount: Long?,
    val warningCount: Long?,
    val clockSkewSeconds: Long?,
    val openAlerts: List<AlertType>,
) {
    constructor(r: ServerRow) : this(
        r.branch.clinic.id, r.branch.clinic.code, r.branch.clinic.name, r.branch.id, r.branch.code, r.branch.name, r.tenantStatus,
        r.deviceId, r.hostname, r.status, r.health?.lastHeartbeatAt, r.health?.appVersion, r.health?.uptimeSeconds,
        r.health?.disks.orEmpty(), r.health?.lastBackupAt, r.health?.lastBackupStatus, r.health?.lastBackupSuccessAt,
        r.health?.errorCount, r.health?.warningCount, r.health?.clockSkewSeconds, r.openAlerts,
    )
}

data class ServerOverviewResponse(val servers: List<ServerRowResponse>, val counts: Map<ServerStatus, Int>, val openAlertCount: Int)

data class AlertResponse(
    val id: UUID,
    val type: AlertType,
    val deviceId: UUID,
    val clinicId: UUID,
    val branchId: UUID,
    val clinicName: String?,
    val branchCode: String?,
    val branchName: String?,
    val details: JsonNode?,
    val openedAt: Instant,
    val resolvedAt: Instant?,
)

@RestController
@RequestMapping("/api/v1/monitoring")
@PreAuthorize(Permissions.HAS_MONITORING_VIEW)
class MonitoringController(private val queries: MonitoringQueryService, private val mapper: JsonMapper) {
    @GetMapping("/servers")
    fun servers(@RequestParam(required = false) status: ServerStatus?, @RequestParam(required = false) q: String?): ServerOverviewResponse {
        val overview = queries.overview(status, q)
        return ServerOverviewResponse(overview.rows.map(::ServerRowResponse), overview.counts, overview.openAlertCount)
    }

    @GetMapping("/alerts")
    fun alerts(
        @RequestParam(defaultValue = "true") open: Boolean,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ) = PageResponse.of(queries.alerts(open, PageQuery.of(page, size)), ::toResponse)

    private fun toResponse(v: AlertView) = AlertResponse(
        v.alert.id, v.alert.type, v.alert.deviceId, v.alert.clinicId, v.alert.branchId, v.branch?.clinic?.name, v.branch?.code,
        v.branch?.name, v.alert.details?.let(mapper::readTree), v.alert.openedAt, v.alert.resolvedAt,
    )
}

@RestController
class HeartbeatController(private val heartbeats: HeartbeatService) {
    @PostMapping(DeviceApi.HEARTBEATS_PATH)
    fun heartbeat(@AuthenticationPrincipal device: AuthenticatedDevice, @RequestBody body: HeartbeatRequest): HeartbeatResponse =
        heartbeats.ingest(device, body)
}
