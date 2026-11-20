package uz.medclinic.vendor.monitoring.infrastructure

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uz.medclinic.common.Ids
import uz.medclinic.vendor.monitoring.api.AlertType
import uz.medclinic.vendor.monitoring.domain.Alert
import uz.medclinic.vendor.monitoring.domain.DeviceHealth
import uz.medclinic.vendor.shared.domain.Page
import uz.medclinic.vendor.shared.domain.PageQuery
import uz.medclinic.vendor.shared.persistence.instant
import uz.medclinic.vendor.shared.persistence.instantOrNull
import uz.medclinic.vendor.shared.persistence.longOrNull
import uz.medclinic.vendor.shared.persistence.toDb
import uz.medclinic.vendor.shared.persistence.toDbOrNull
import uz.medclinic.vendor.shared.persistence.uuid
import uz.medclinic.vendor.shared.persistence.uuidOrNull
import uz.medclinic.vendorprotocol.BackupStatus
import uz.medclinic.vendorprotocol.DiskUsage
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@Repository
class JdbcMonitoringRepository(private val jdbc: JdbcClient, private val mapper: JsonMapper) {
    /** Returns false when this heartbeat id was already stored (client retry). */
    fun insertHeartbeat(id: UUID, deviceId: UUID, receivedAt: Instant, sentAt: Instant, payload: Any): Boolean =
        jdbc.sql(
            """
            insert into heartbeat (id, device_id, received_at, sent_at, payload)
            values (:id, :deviceId, :receivedAt, :sentAt, cast(:payload as jsonb))
            on conflict (id) do nothing
            """,
        )
            .param("id", id).param("deviceId", deviceId).param("receivedAt", receivedAt.toDb())
            .param("sentAt", sentAt.toDb()).param("payload", mapper.writeValueAsString(payload))
            .update() > 0

    fun upsertHealth(h: DeviceHealth) {
        jdbc.sql(
            """
            insert into device_health (device_id, clinic_id, branch_id, last_heartbeat_at, app_version, uptime_seconds, disks,
                                       last_backup_at, last_backup_status, last_backup_success_at, last_backup_size_bytes,
                                       error_count, warning_count, clock_skew_seconds, installed_license_id)
            values (:deviceId, :clinicId, :branchId, :lastHeartbeatAt, :appVersion, :uptime, cast(:disks as jsonb),
                    :lastBackupAt, :lastBackupStatus, :lastBackupSuccessAt, :lastBackupSize,
                    :errorCount, :warningCount, :skew, :licenseId)
            on conflict (device_id) do update set
                last_heartbeat_at = excluded.last_heartbeat_at, app_version = excluded.app_version,
                uptime_seconds = excluded.uptime_seconds, disks = excluded.disks, last_backup_at = excluded.last_backup_at,
                last_backup_status = excluded.last_backup_status, last_backup_success_at = excluded.last_backup_success_at,
                last_backup_size_bytes = excluded.last_backup_size_bytes, error_count = excluded.error_count,
                warning_count = excluded.warning_count, clock_skew_seconds = excluded.clock_skew_seconds,
                installed_license_id = excluded.installed_license_id
            where device_health.last_heartbeat_at <= excluded.last_heartbeat_at
            """,
        )
            .param("deviceId", h.deviceId).param("clinicId", h.clinicId).param("branchId", h.branchId)
            .param("lastHeartbeatAt", h.lastHeartbeatAt.toDb()).param("appVersion", h.appVersion).param("uptime", h.uptimeSeconds)
            .param("disks", mapper.writeValueAsString(h.disks)).param("lastBackupAt", h.lastBackupAt.toDbOrNull())
            .param("lastBackupStatus", h.lastBackupStatus?.name).param("lastBackupSuccessAt", h.lastBackupSuccessAt.toDbOrNull())
            .param("lastBackupSize", h.lastBackupSizeBytes).param("errorCount", h.errorCount).param("warningCount", h.warningCount)
            .param("skew", h.clockSkewSeconds).param("licenseId", h.installedLicenseId)
            .update()
    }

    fun findHealth(deviceIds: Collection<UUID>): Map<UUID, DeviceHealth> {
        if (deviceIds.isEmpty()) return emptyMap()
        return jdbc.sql("select * from device_health where device_id in (:ids)").param("ids", deviceIds)
            .query(::mapHealth).list().associateBy { it.deviceId }
    }

    /** Opens an alert unless one of the same type is already open; returns the new alert or null. */
    fun openAlert(deviceId: UUID, clinicId: UUID, branchId: UUID, type: AlertType, details: Any?, at: Instant): Alert? {
        val id = Ids.newId()
        val inserted = jdbc.sql(
            """
            insert into monitoring_alert (id, device_id, clinic_id, branch_id, type, details, opened_at)
            values (:id, :deviceId, :clinicId, :branchId, :type, cast(:details as jsonb), :at)
            on conflict (device_id, type) where resolved_at is null do nothing
            """,
        )
            .param("id", id).param("deviceId", deviceId).param("clinicId", clinicId).param("branchId", branchId)
            .param("type", type.name).param("details", details?.let(mapper::writeValueAsString)).param("at", at.toDb())
            .update()
        return if (inserted > 0) Alert(id, deviceId, clinicId, branchId, type, null, at, null) else null
    }

    /** Resolves open alerts of a device (all types when [types] is null); returns the resolved alerts. */
    fun resolveAlerts(deviceId: UUID, types: Collection<AlertType>?, at: Instant): List<Alert> =
        jdbc.sql(
            """
            update monitoring_alert set resolved_at = :at
            where device_id = :deviceId and resolved_at is null ${if (types == null) "" else "and type in (:types)"}
            returning id, device_id, clinic_id, branch_id, type, details::text as details, opened_at, resolved_at
            """,
        ).params(
            buildMap {
                put("deviceId", deviceId); put("at", at.toDb())
                if (types != null) put("types", types.map { it.name }.ifEmpty { listOf("-") })
            },
        ).query(::mapAlert).list()

    fun openAlertDeviceIds(): List<UUID> =
        jdbc.sql("select distinct device_id from monitoring_alert where resolved_at is null").query { rs, _ -> rs.uuid("device_id") }.list()

    fun openAlerts(): List<Alert> =
        jdbc.sql("select id, device_id, clinic_id, branch_id, type, details::text as details, opened_at, resolved_at from monitoring_alert where resolved_at is null order by opened_at desc")
            .query(::mapAlert).list()

    fun alerts(openOnly: Boolean, page: PageQuery): Page<Alert> {
        val where = if (openOnly) "where resolved_at is null" else ""
        val total = jdbc.sql("select count(*) from monitoring_alert $where").query(Long::class.java).single()
        val items = jdbc.sql(
            """
            select id, device_id, clinic_id, branch_id, type, details::text as details, opened_at, resolved_at
            from monitoring_alert $where order by opened_at desc, id desc limit :limit offset :offset
            """,
        ).param("limit", page.size).param("offset", page.offset).query(::mapAlert).list()
        return Page(items, page.page, page.size, total)
    }

    fun purgeHeartbeatsBefore(cutoff: Instant): Int =
        jdbc.sql("delete from heartbeat where received_at < :cutoff").param("cutoff", cutoff.toDb()).update()

    private fun mapHealth(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = DeviceHealth(
        deviceId = rs.uuid("device_id"),
        clinicId = rs.uuid("clinic_id"),
        branchId = rs.uuid("branch_id"),
        lastHeartbeatAt = rs.instant("last_heartbeat_at"),
        appVersion = rs.getString("app_version"),
        uptimeSeconds = rs.getLong("uptime_seconds"),
        disks = mapper.readValue<List<DiskUsage>>(rs.getString("disks")),
        lastBackupAt = rs.instantOrNull("last_backup_at"),
        lastBackupStatus = rs.getString("last_backup_status")?.let(BackupStatus::valueOf),
        lastBackupSuccessAt = rs.instantOrNull("last_backup_success_at"),
        lastBackupSizeBytes = rs.longOrNull("last_backup_size_bytes"),
        errorCount = rs.longOrNull("error_count"),
        warningCount = rs.longOrNull("warning_count"),
        clockSkewSeconds = rs.getLong("clock_skew_seconds"),
        installedLicenseId = rs.uuidOrNull("installed_license_id"),
    )

    private fun mapAlert(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = Alert(
        id = rs.uuid("id"),
        deviceId = rs.uuid("device_id"),
        clinicId = rs.uuid("clinic_id"),
        branchId = rs.uuid("branch_id"),
        type = AlertType.valueOf(rs.getString("type")),
        details = rs.getString("details"),
        openedAt = rs.instant("opened_at"),
        resolvedAt = rs.instantOrNull("resolved_at"),
    )
}
