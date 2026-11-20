package uz.medclinic.license

import java.time.Duration
import java.time.Instant

/** Time-based state of a (signature-valid) license. Revocation/supersession is tracked by the vendor, not here. */
enum class LicenseTimeStatus { PENDING, ACTIVE, GRACE, EXPIRED }

data class LicenseValidity(
    val status: LicenseTimeStatus,
    val graceEndsAt: Instant,
) {
    /** The clinic may operate normally (with a warning in GRACE). */
    val usable: Boolean get() = status == LicenseTimeStatus.ACTIVE || status == LicenseTimeStatus.GRACE

    companion object {
        fun evaluate(payload: LicensePayload, now: Instant): LicenseValidity =
            evaluate(payload.validFrom, payload.validUntil, payload.expiryGraceDays, now)

        /** Single source of the time rule, shared by the vendor (status display) and the Clinic Server. */
        fun evaluate(validFrom: Instant, validUntil: Instant, expiryGraceDays: Int, now: Instant): LicenseValidity {
            val graceEndsAt = validUntil.plus(Duration.ofDays(expiryGraceDays.toLong()))
            val status = when {
                now.isBefore(validFrom) -> LicenseTimeStatus.PENDING
                now.isBefore(validUntil) -> LicenseTimeStatus.ACTIVE
                now.isBefore(graceEndsAt) -> LicenseTimeStatus.GRACE
                else -> LicenseTimeStatus.EXPIRED
            }
            return LicenseValidity(status, graceEndsAt)
        }
    }
}
