package uz.medclinic.vendor.licensing.infrastructure

import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import uz.medclinic.vendor.licensing.domain.License
import uz.medclinic.vendor.licensing.domain.LicenseRepository
import uz.medclinic.vendor.licensing.domain.LicenseTerms
import uz.medclinic.vendor.shared.domain.DomainException
import uz.medclinic.vendor.shared.persistence.instant
import uz.medclinic.vendor.shared.persistence.instantOrNull
import uz.medclinic.vendor.shared.persistence.requireVersionMatched
import uz.medclinic.vendor.shared.persistence.toDb
import uz.medclinic.vendor.shared.persistence.toDbOrNull
import uz.medclinic.vendor.shared.persistence.uuid
import uz.medclinic.vendor.shared.persistence.uuidOrNull
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Repository
class JdbcLicenseRepository(private val jdbc: JdbcClient) : LicenseRepository {
    override fun nextSerial(): Long = jdbc.sql("select nextval('license_serial_seq')").query(Long::class.java).single()

    override fun insert(license: License) {
        val t = license.terms
        try {
            jdbc.sql(
                """
                insert into license (id, clinic_id, serial, plan, modules, max_users, max_branches, starts_on, ends_on,
                                     valid_from, valid_until, expiry_grace_days, issued_at, issued_by, key_id, payload,
                                     signature, renewed_from, version)
                values (:id, :clinicId, :serial, :plan, :modules, :maxUsers, :maxBranches, :startsOn, :endsOn,
                        :validFrom, :validUntil, :grace, :issuedAt, :issuedBy, :keyId, :payload, :signature, :renewedFrom, 0)
                """,
            )
                .param("id", license.id).param("clinicId", license.clinicId).param("serial", license.serial)
                .param("plan", t.plan).param("modules", t.modules.toTypedArray())
                .param("maxUsers", t.maxUsers).param("maxBranches", t.maxBranches)
                .param("startsOn", t.startsOn).param("endsOn", t.endsOn)
                .param("validFrom", t.validFrom.toDb()).param("validUntil", t.validUntil.toDb())
                .param("grace", t.expiryGraceDays).param("issuedAt", license.issuedAt.toDb())
                .param("issuedBy", license.issuedBy).param("keyId", license.keyId)
                .param("payload", license.payload).param("signature", license.signature)
                .param("renewedFrom", license.renewedFrom)
                .update()
        } catch (_: DuplicateKeyException) {
            throw DomainException("license.current_exists", DomainException.Kind.CONFLICT)
        }
    }

    override fun findById(id: UUID): License? =
        jdbc.sql("select * from license where id = :id").param("id", id).query(::map).optional().orElse(null)

    override fun findByClinic(clinicId: UUID): List<License> =
        jdbc.sql("select * from license where clinic_id = :clinicId order by serial desc").param("clinicId", clinicId).query(::map).list()

    override fun findCurrent(clinicId: UUID): License? =
        jdbc.sql("select * from license where clinic_id = :clinicId and revoked_at is null and superseded_at is null")
            .param("clinicId", clinicId).query(::map).optional().orElse(null)

    override fun markSuperseded(id: UUID, at: Instant, expectedVersion: Int): License {
        val rows = jdbc.sql(
            """
            update license set superseded_at = :at, version = version + 1
            where id = :id and version = :version and superseded_at is null and revoked_at is null
            """,
        ).param("id", id).param("at", at.toDb()).param("version", expectedVersion).update()
        requireVersionMatched(rows, "license", id)
        return findById(id)!!
    }

    override fun linkSuccessor(id: UUID, successorId: UUID) {
        jdbc.sql("update license set superseded_by = :successor where id = :id").param("id", id).param("successor", successorId).update()
    }

    override fun update(license: License): License {
        val rows = jdbc.sql(
            """
            update license set revoked_at = :revokedAt, revoked_by = :revokedBy, revoke_reason = :reason, version = version + 1
            where id = :id and version = :version
            """,
        )
            .param("id", license.id).param("version", license.version)
            .param("revokedAt", license.revokedAt.toDbOrNull()).param("revokedBy", license.revokedBy).param("reason", license.revokeReason)
            .update()
        requireVersionMatched(rows, "license", license.id)
        return license.copy(version = license.version + 1)
    }

    @Suppress("UNCHECKED_CAST")
    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = License(
        id = rs.uuid("id"),
        clinicId = rs.uuid("clinic_id"),
        serial = rs.getLong("serial"),
        terms = LicenseTerms(
            plan = rs.getString("plan"),
            modules = (rs.getArray("modules").array as Array<String>).toList(),
            maxUsers = rs.getInt("max_users"),
            maxBranches = rs.getInt("max_branches"),
            startsOn = rs.getObject("starts_on", LocalDate::class.java),
            endsOn = rs.getObject("ends_on", LocalDate::class.java),
            expiryGraceDays = rs.getInt("expiry_grace_days"),
        ),
        issuedAt = rs.instant("issued_at"),
        issuedBy = rs.uuid("issued_by"),
        keyId = rs.getString("key_id"),
        payload = rs.getBytes("payload"),
        signature = rs.getBytes("signature"),
        renewedFrom = rs.uuidOrNull("renewed_from"),
        supersededAt = rs.instantOrNull("superseded_at"),
        supersededBy = rs.uuidOrNull("superseded_by"),
        revokedAt = rs.instantOrNull("revoked_at"),
        revokedBy = rs.uuidOrNull("revoked_by"),
        revokeReason = rs.getString("revoke_reason"),
        version = rs.getInt("version"),
    )
}
