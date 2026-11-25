package uz.medclinic.clinic.settings.infrastructure

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uz.medclinic.clinic.settings.domain.ClinicSettings
import uz.medclinic.clinic.settings.domain.WorkingDay
import uz.medclinic.clinic.shared.ClinicIdentity
import uz.medclinic.common.UzPhone
import uz.medclinic.platform.persistence.instant
import uz.medclinic.platform.persistence.requireVersionMatched
import uz.medclinic.platform.persistence.toDb
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@Repository
class JdbcSettingsRepository(private val jdbc: JdbcClient, private val mapper: JsonMapper) {
    fun find(): ClinicSettings? =
        jdbc.sql("select *, logo is not null as has_logo from clinic_settings where id = 1").query(::map).optional().orElse(null)

    fun insertIfMissing(identity: ClinicIdentity, name: String, at: Instant): Boolean =
        jdbc.sql(
            """
            insert into clinic_settings (id, clinic_id, branch_id, name, working_hours, updated_at)
            values (1, :clinicId, :branchId, :name, cast(:hours as jsonb), :at)
            on conflict (id) do nothing
            """,
        )
            .param("clinicId", identity.clinicId).param("branchId", identity.branchId).param("name", name)
            .param("hours", mapper.writeValueAsString(WorkingDay.defaults())).param("at", at.toDb())
            .update() > 0

    fun update(edit: ClinicSettings.Edit, expectedVersion: Int, at: Instant) {
        val rows = jdbc.sql(
            """
            update clinic_settings
            set name = :name, legal_name = :legalName, address = :address, phone = :phone, letterhead = :letterhead,
                receipt_header = :receiptHeader, receipt_footer = :receiptFooter, working_hours = cast(:hours as jsonb),
                idle_lock_minutes = :idle, updated_at = :at, version = version + 1
            where id = 1 and version = :version
            """,
        )
            .param("name", edit.name).param("legalName", edit.legalName).param("address", edit.address)
            .param("phone", edit.phone?.e164).param("letterhead", edit.letterhead).param("receiptHeader", edit.receiptHeader)
            .param("receiptFooter", edit.receiptFooter).param("hours", mapper.writeValueAsString(edit.workingHours))
            .param("idle", edit.idleLockMinutes).param("at", at.toDb()).param("version", expectedVersion)
            .update()
        requireVersionMatched(rows, "settings", SETTINGS_ID)
    }

    fun logo(): Pair<ByteArray, String>? =
        jdbc.sql("select logo, logo_type from clinic_settings where id = 1 and logo is not null")
            .query { rs, _ -> rs.getBytes("logo") to rs.getString("logo_type") }.optional().orElse(null)

    fun setLogo(bytes: ByteArray?, type: String?, at: Instant) {
        jdbc.sql("update clinic_settings set logo = :bytes, logo_type = :type, updated_at = :at, version = version + 1 where id = 1")
            .param("bytes", bytes).param("type", type).param("at", at.toDb()).update()
    }

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = ClinicSettings(
        name = rs.getString("name"),
        legalName = rs.getString("legal_name"),
        address = rs.getString("address"),
        phone = rs.getString("phone")?.let(UzPhone::of),
        letterhead = rs.getString("letterhead"),
        receiptHeader = rs.getString("receipt_header"),
        receiptFooter = rs.getString("receipt_footer"),
        workingHours = mapper.readValue<List<WorkingDay>>(rs.getString("working_hours")),
        idleLockMinutes = rs.getInt("idle_lock_minutes"),
        hasLogo = rs.getBoolean("has_logo"),
        updatedAt = rs.instant("updated_at"),
        version = rs.getInt("version"),
    )

    private companion object {
        /** Entity id for conflict reporting of the single settings row. */
        val SETTINGS_ID: UUID = UUID(0, 1)
    }
}
