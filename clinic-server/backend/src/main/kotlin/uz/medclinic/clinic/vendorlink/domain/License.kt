package uz.medclinic.clinic.vendorlink.domain

import uz.medclinic.license.LicensePayload
import uz.medclinic.license.LicenseTimeStatus
import uz.medclinic.license.LicenseValidity
import java.time.Instant

/** What the clinic may do right now according to its license and its clock. */
enum class LicenseState(val usable: Boolean) {
    /** Paired but the vendor has not issued a license yet (or none was installed). */
    NONE(false),
    PENDING(false),
    ACTIVE(true),

    /** Expired but within the grace days: work continues with a warning. */
    GRACE(true),
    EXPIRED(false),
    REVOKED(false),

    /** The installed file does not verify (tampered or signed with an unknown key). */
    INVALID(false),

    /** The system clock was moved backwards; work stops until the time is correct again. */
    CLOCK_ROLLBACK(false),
}

data class LicenseStatus(
    val state: LicenseState,
    val payload: LicensePayload?,
    val graceEndsAt: Instant?,
    val clockWarning: Boolean,
) {
    val usable: Boolean get() = state.usable

    companion object {
        /**
         * The single decision table for the clinic. Order matters: a moved-back clock wins over everything,
         * because time-based checks cannot be trusted then.
         */
        fun evaluate(
            payload: LicensePayload?,
            invalid: Boolean,
            revoked: Boolean,
            clockRolledBack: Boolean,
            clockWarning: Boolean,
            now: Instant,
        ): LicenseStatus {
            if (clockRolledBack) return LicenseStatus(LicenseState.CLOCK_ROLLBACK, payload, null, clockWarning)
            if (invalid) return LicenseStatus(LicenseState.INVALID, null, null, clockWarning)
            if (payload == null) return LicenseStatus(LicenseState.NONE, null, null, clockWarning)
            val validity = LicenseValidity.evaluate(payload, now)
            if (revoked) return LicenseStatus(LicenseState.REVOKED, payload, validity.graceEndsAt, clockWarning)
            val state = when (validity.status) {
                LicenseTimeStatus.PENDING -> LicenseState.PENDING
                LicenseTimeStatus.ACTIVE -> LicenseState.ACTIVE
                LicenseTimeStatus.GRACE -> LicenseState.GRACE
                LicenseTimeStatus.EXPIRED -> LicenseState.EXPIRED
            }
            return LicenseStatus(state, payload, validity.graceEndsAt, clockWarning)
        }
    }
}

/** Detects the system clock being moved backwards (to stretch an expired license). */
object ClockGuard {
    fun rolledBack(now: Instant, maxSeen: Instant?, tolerance: java.time.Duration): Boolean =
        maxSeen != null && now.isBefore(maxSeen.minus(tolerance))
}
