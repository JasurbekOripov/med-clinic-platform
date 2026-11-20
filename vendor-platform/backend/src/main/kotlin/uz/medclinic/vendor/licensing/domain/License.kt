package uz.medclinic.vendor.licensing.domain

import uz.medclinic.common.TashkentTime
import uz.medclinic.license.LicenseModules
import uz.medclinic.license.LicensePayload
import uz.medclinic.license.LicenseTimeStatus
import uz.medclinic.license.LicenseValidity
import uz.medclinic.vendor.shared.domain.DomainException
import uz.medclinic.vendor.shared.domain.FieldViolation
import uz.medclinic.vendor.shared.domain.Text
import uz.medclinic.vendor.shared.domain.ValidationException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class LicenseStatus { PENDING, ACTIVE, GRACE, EXPIRED, SUPERSEDED, REVOKED }

/** Commercial terms of a license, validated. Dates are Tashkent calendar days, [endsOn] inclusive. */
data class LicenseTerms(
    val plan: String,
    val modules: List<String>,
    val maxUsers: Int,
    val maxBranches: Int,
    val startsOn: LocalDate,
    val endsOn: LocalDate,
    val expiryGraceDays: Int,
) {
    val validFrom: Instant get() = TashkentTime.startOfDay(startsOn)
    val validUntil: Instant get() = TashkentTime.endOfDayExclusive(endsOn)

    companion object {
        private val PLAN = Regex("^[A-Z0-9_]{2,50}$")
        const val MAX_USERS = 10_000
        const val MAX_BRANCHES = 500
        const val DEFAULT_GRACE_DAYS = 14

        fun of(
            plan: String?,
            modules: List<String>?,
            maxUsers: Int?,
            maxBranches: Int?,
            startsOn: LocalDate?,
            endsOn: LocalDate?,
            expiryGraceDays: Int?,
        ): LicenseTerms {
            val errors = mutableListOf<FieldViolation>()
            val cleanPlan = Text.clean(plan)?.uppercase()?.replace(' ', '_')
            if (cleanPlan == null || !PLAN.matches(cleanPlan)) errors += FieldViolation("plan", "validation.plan_format")
            val cleanModules = modules.orEmpty().map { it.trim().uppercase() }.distinct()
            if (cleanModules.isEmpty()) errors += FieldViolation("modules", "validation.not_empty")
            val unknown = cleanModules - LicenseModules.ALL.toSet()
            if (unknown.isNotEmpty()) errors += FieldViolation("modules", "validation.unknown_modules", mapOf("modules" to unknown))
            if (maxUsers == null || maxUsers !in 1..MAX_USERS) errors += FieldViolation("maxUsers", "validation.range", mapOf("min" to 1, "max" to MAX_USERS))
            if (maxBranches == null || maxBranches !in 1..MAX_BRANCHES) {
                errors += FieldViolation("maxBranches", "validation.range", mapOf("min" to 1, "max" to MAX_BRANCHES))
            }
            if (startsOn == null) errors += FieldViolation("startsOn", "validation.not_null")
            if (endsOn == null) errors += FieldViolation("endsOn", "validation.not_null")
            if (startsOn != null && endsOn != null && endsOn.isBefore(startsOn)) errors += FieldViolation("endsOn", "validation.ends_before_start")
            val grace = expiryGraceDays ?: DEFAULT_GRACE_DAYS
            if (grace !in 0..LicensePayload.MAX_GRACE_DAYS) {
                errors += FieldViolation("expiryGraceDays", "validation.range", mapOf("min" to 0, "max" to LicensePayload.MAX_GRACE_DAYS))
            }
            if (errors.isNotEmpty()) throw ValidationException(errors)
            // Keep the catalogue order so equal module sets always produce equal payloads.
            val ordered = LicenseModules.ALL.filter { it in cleanModules }
            return LicenseTerms(cleanPlan!!, ordered, maxUsers!!, maxBranches!!, startsOn!!, endsOn!!, grace)
        }
    }
}

data class License(
    val id: UUID,
    val clinicId: UUID,
    val serial: Long,
    val terms: LicenseTerms,
    val issuedAt: Instant,
    val issuedBy: UUID,
    val keyId: String,
    val payload: ByteArray,
    val signature: ByteArray,
    val renewedFrom: UUID?,
    val supersededAt: Instant?,
    val supersededBy: UUID?,
    val revokedAt: Instant?,
    val revokedBy: UUID?,
    val revokeReason: String?,
    val version: Int,
) {
    /** Current = still the clinic's license of record (it may still be pending, in grace or expired by time). */
    val current: Boolean get() = revokedAt == null && supersededAt == null

    fun graceEndsAt(): Instant = validity(issuedAt).graceEndsAt

    fun status(now: Instant): LicenseStatus = when {
        revokedAt != null -> LicenseStatus.REVOKED
        supersededAt != null -> LicenseStatus.SUPERSEDED
        else -> when (validity(now).status) {
            LicenseTimeStatus.PENDING -> LicenseStatus.PENDING
            LicenseTimeStatus.ACTIVE -> LicenseStatus.ACTIVE
            LicenseTimeStatus.GRACE -> LicenseStatus.GRACE
            LicenseTimeStatus.EXPIRED -> LicenseStatus.EXPIRED
        }
    }

    private fun validity(now: Instant) = LicenseValidity.evaluate(terms.validFrom, terms.validUntil, terms.expiryGraceDays, now)

    fun revoked(by: UUID, reason: String, now: Instant): License {
        if (revokedAt != null) throw DomainException("license.already_revoked")
        return copy(revokedAt = now, revokedBy = by, revokeReason = reason)
    }

    override fun equals(other: Any?): Boolean = other is License && other.id == id && other.version == version
    override fun hashCode(): Int = id.hashCode()
}

interface LicenseRepository {
    fun nextSerial(): Long
    fun insert(license: License)
    fun findById(id: UUID): License?
    fun findByClinic(clinicId: UUID): List<License>
    fun findCurrent(clinicId: UUID): License?
    fun markSuperseded(id: UUID, at: Instant, expectedVersion: Int): License
    fun linkSuccessor(id: UUID, successorId: UUID)
    fun update(license: License): License
}
