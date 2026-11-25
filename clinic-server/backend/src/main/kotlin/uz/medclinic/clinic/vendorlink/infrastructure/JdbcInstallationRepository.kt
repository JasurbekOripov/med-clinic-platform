package uz.medclinic.clinic.vendorlink.infrastructure

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import uz.medclinic.platform.persistence.instant
import uz.medclinic.platform.persistence.instantOrNull
import uz.medclinic.platform.persistence.toDb
import uz.medclinic.platform.persistence.toDbOrNull
import uz.medclinic.platform.persistence.uuid
import java.time.Instant
import java.util.UUID

data class Installation(
    val clinicId: UUID,
    val branchId: UUID,
    val clinicCode: String,
    val branchCode: String,
    val vendorUrl: String,
    val deviceId: UUID,
    val deviceTokenEnc: ByteArray,
    val pairedAt: Instant,
)

data class InstalledLicense(val licenseId: UUID, val file: ByteArray, val installedAt: Instant, val revokedAt: Instant?)

data class LinkState(
    val lastAttemptAt: Instant?,
    val lastSuccessAt: Instant?,
    val lastErrorCode: String?,
    val lastServerTime: Instant?,
    val maxSeenTime: Instant?,
)

@Repository
class JdbcInstallationRepository(private val jdbc: JdbcClient) {
    fun find(): Installation? =
        jdbc.sql("select * from installation where id = 1").query { rs, _ ->
            Installation(
                rs.uuid("clinic_id"), rs.uuid("branch_id"), rs.getString("clinic_code"), rs.getString("branch_code"),
                rs.getString("vendor_url"), rs.uuid("device_id"), rs.getBytes("device_token_enc"), rs.instant("paired_at"),
            )
        }.optional().orElse(null)

    /** Returns false when the server is already paired (the row exists). */
    fun insert(i: Installation): Boolean =
        jdbc.sql(
            """
            insert into installation (id, clinic_id, branch_id, clinic_code, branch_code, vendor_url, device_id, device_token_enc, paired_at)
            values (1, :clinicId, :branchId, :clinicCode, :branchCode, :vendorUrl, :deviceId, :token, :pairedAt)
            on conflict (id) do nothing
            """,
        )
            .param("clinicId", i.clinicId).param("branchId", i.branchId).param("clinicCode", i.clinicCode)
            .param("branchCode", i.branchCode).param("vendorUrl", i.vendorUrl).param("deviceId", i.deviceId)
            .param("token", i.deviceTokenEnc).param("pairedAt", i.pairedAt.toDb())
            .update() > 0

    fun findLicense(): InstalledLicense? =
        jdbc.sql("select * from installed_license where id = 1").query { rs, _ ->
            InstalledLicense(rs.uuid("license_id"), rs.getBytes("file"), rs.instant("installed_at"), rs.instantOrNull("revoked_at"))
        }.optional().orElse(null)

    fun saveLicense(clinicId: UUID, branchId: UUID, licenseId: UUID, file: ByteArray, at: Instant) {
        jdbc.sql(
            """
            insert into installed_license (id, clinic_id, branch_id, license_id, file, installed_at, revoked_at)
            values (1, :clinicId, :branchId, :licenseId, :file, :at, null)
            on conflict (id) do update set license_id = excluded.license_id, file = excluded.file,
                                           installed_at = excluded.installed_at, revoked_at = null
            """,
        ).param("clinicId", clinicId).param("branchId", branchId).param("licenseId", licenseId).param("file", file)
            .param("at", at.toDb()).update()
    }

    fun markLicenseRevoked(licenseId: UUID, at: Instant): Boolean =
        jdbc.sql("update installed_license set revoked_at = :at where license_id = :id and revoked_at is null")
            .param("id", licenseId).param("at", at.toDb()).update() > 0

    fun state(): LinkState =
        jdbc.sql("select * from vendor_link_state where id = 1").query { rs, _ ->
            LinkState(
                rs.instantOrNull("last_attempt_at"), rs.instantOrNull("last_success_at"), rs.getString("last_error_code"),
                rs.instantOrNull("last_server_time"), rs.instantOrNull("max_seen_time"),
            )
        }.single()

    fun recordAttempt(at: Instant, success: Boolean, errorCode: String?, serverTime: Instant?) {
        jdbc.sql(
            """
            update vendor_link_state
            set last_attempt_at = :at,
                last_success_at = case when :success then :at else last_success_at end,
                last_error_code = :error,
                last_server_time = coalesce(:serverTime, last_server_time)
            where id = 1
            """,
        ).param("at", at.toDb()).param("success", success).param("error", errorCode)
            .param("serverTime", serverTime.toDbOrNull()).update()
    }

    /**
     * The vendor confirmed the local clock is right: trust it as the new high-water mark. This also undoes an
     * accidental jump into the future (which would otherwise block work until that date).
     */
    fun trustTime(now: Instant) {
        jdbc.sql("update vendor_link_state set max_seen_time = :now where id = 1").param("now", now.toDb()).update()
    }

    /** Raises the high-water mark of observed local time (never lowers it). */
    fun observeTime(now: Instant) {
        jdbc.sql("update vendor_link_state set max_seen_time = greatest(coalesce(max_seen_time, :now), :now) where id = 1")
            .param("now", now.toDb()).update()
    }
}
