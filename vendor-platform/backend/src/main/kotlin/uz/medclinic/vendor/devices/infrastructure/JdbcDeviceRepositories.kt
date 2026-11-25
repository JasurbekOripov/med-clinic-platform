package uz.medclinic.vendor.devices.infrastructure

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import uz.medclinic.vendor.devices.domain.Device
import uz.medclinic.vendor.devices.domain.DeviceRepository
import uz.medclinic.vendor.devices.domain.DeviceStatus
import uz.medclinic.vendor.devices.domain.PairingCode
import uz.medclinic.vendor.devices.domain.PairingCodeRepository
import uz.medclinic.platform.persistence.instant
import uz.medclinic.platform.persistence.instantOrNull
import uz.medclinic.platform.persistence.requireVersionMatched
import uz.medclinic.platform.persistence.toDb
import uz.medclinic.platform.persistence.uuid
import uz.medclinic.platform.persistence.uuidOrNull
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@Repository
class JdbcDeviceRepository(private val jdbc: JdbcClient) : DeviceRepository {
    override fun insert(device: Device) {
        jdbc.sql(
            """
            insert into device (id, clinic_id, branch_id, token_hash, status, hostname, os, app_version, paired_at, last_ip, version)
            values (:id, :clinicId, :branchId, :tokenHash, :status, :hostname, :os, :appVersion, :pairedAt, :lastIp, 0)
            """,
        )
            .param("id", device.id).param("clinicId", device.clinicId).param("branchId", device.branchId)
            .param("tokenHash", device.tokenHash).param("status", device.status.name).param("hostname", device.hostname)
            .param("os", device.os).param("appVersion", device.appVersion).param("pairedAt", device.pairedAt.toDb())
            .param("lastIp", device.lastIp)
            .update()
    }

    override fun findById(id: UUID): Device? = one("select * from device where id = :id", "id", id)

    override fun findActiveByTokenHash(tokenHash: String): Device? =
        one("select * from device where token_hash = :hash and status = 'ACTIVE'", "hash", tokenHash)

    override fun findByBranch(branchId: UUID): List<Device> =
        jdbc.sql("select * from device where branch_id = :branchId order by paired_at desc, id desc").param("branchId", branchId).query(::map).list()

    override fun findActive(): List<Device> = jdbc.sql("select * from device where status = 'ACTIVE'").query(::map).list()

    override fun findActiveByBranch(branchId: UUID): Device? =
        one("select * from device where branch_id = :branchId and status = 'ACTIVE' for update", "branchId", branchId)

    override fun findActiveByClinic(clinicId: UUID): List<Device> =
        jdbc.sql("select * from device where clinic_id = :clinicId and status = 'ACTIVE'").param("clinicId", clinicId).query(::map).list()

    override fun revoke(id: UUID, reason: String, at: Instant, expectedVersion: Int?): Device {
        val rows = jdbc.sql(
            """
            update device set status = 'REVOKED', revoked_at = :at, revoke_reason = :reason, version = version + 1
            where id = :id and status = 'ACTIVE' and (cast(:version as int) is null or version = :version)
            """,
        ).param("id", id).param("at", at.toDb()).param("reason", reason).param("version", expectedVersion).update()
        requireVersionMatched(rows, "device", id)
        return findById(id)!!
    }

    override fun touch(id: UUID, at: Instant, ip: String?) {
        jdbc.sql("update device set last_seen_at = :at, last_ip = :ip where id = :id")
            .param("id", id).param("at", at.toDb()).param("ip", ip).update()
    }

    private fun one(sql: String, name: String, value: Any): Device? =
        jdbc.sql(sql).param(name, value).query(::map).optional().orElse(null)

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = Device(
        id = rs.uuid("id"),
        clinicId = rs.uuid("clinic_id"),
        branchId = rs.uuid("branch_id"),
        tokenHash = rs.getString("token_hash"),
        status = DeviceStatus.valueOf(rs.getString("status")),
        hostname = rs.getString("hostname"),
        os = rs.getString("os"),
        appVersion = rs.getString("app_version"),
        pairedAt = rs.instant("paired_at"),
        revokedAt = rs.instantOrNull("revoked_at"),
        revokeReason = rs.getString("revoke_reason"),
        lastSeenAt = rs.instantOrNull("last_seen_at"),
        lastIp = rs.getString("last_ip"),
        version = rs.getInt("version"),
    )
}

@Repository
class JdbcPairingCodeRepository(private val jdbc: JdbcClient) : PairingCodeRepository {
    override fun insert(code: PairingCode) {
        jdbc.sql(
            """
            insert into pairing_code (id, clinic_id, branch_id, code_hash, expires_at, created_by, created_at)
            values (:id, :clinicId, :branchId, :codeHash, :expiresAt, :createdBy, :createdAt)
            """,
        )
            .param("id", code.id).param("clinicId", code.clinicId).param("branchId", code.branchId)
            .param("codeHash", code.codeHash).param("expiresAt", code.expiresAt.toDb())
            .param("createdBy", code.createdBy).param("createdAt", code.createdAt.toDb())
            .update()
    }

    override fun findById(id: UUID): PairingCode? =
        jdbc.sql("select * from pairing_code where id = :id").param("id", id).query(::map).optional().orElse(null)

    /** Row lock: two installers racing with the same code cannot both claim it. */
    override fun findByHashForUpdate(codeHash: String): PairingCode? =
        jdbc.sql("select * from pairing_code where code_hash = :hash for update").param("hash", codeHash).query(::map).optional().orElse(null)

    override fun findByBranch(branchId: UUID, limit: Int): List<PairingCode> =
        jdbc.sql("select * from pairing_code where branch_id = :branchId order by created_at desc, id desc limit :limit")
            .param("branchId", branchId).param("limit", limit).query(::map).list()

    override fun markClaimed(id: UUID, deviceId: UUID, at: Instant) {
        jdbc.sql("update pairing_code set claimed_at = :at, claimed_device_id = :deviceId where id = :id")
            .param("id", id).param("deviceId", deviceId).param("at", at.toDb()).update()
    }

    override fun cancel(id: UUID, at: Instant): Boolean =
        jdbc.sql("update pairing_code set cancelled_at = :at where id = :id and claimed_at is null and cancelled_at is null")
            .param("id", id).param("at", at.toDb()).update() > 0

    override fun cancelOpenForBranch(branchId: UUID, at: Instant) {
        jdbc.sql("update pairing_code set cancelled_at = :at where branch_id = :branchId and claimed_at is null and cancelled_at is null")
            .param("branchId", branchId).param("at", at.toDb()).update()
    }

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = PairingCode(
        id = rs.uuid("id"),
        clinicId = rs.uuid("clinic_id"),
        branchId = rs.uuid("branch_id"),
        codeHash = rs.getString("code_hash"),
        expiresAt = rs.instant("expires_at"),
        createdBy = rs.uuid("created_by"),
        createdAt = rs.instant("created_at"),
        claimedAt = rs.instantOrNull("claimed_at"),
        claimedDeviceId = rs.uuidOrNull("claimed_device_id"),
        cancelledAt = rs.instantOrNull("cancelled_at"),
    )
}
