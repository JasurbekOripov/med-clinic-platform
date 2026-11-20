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
        fun evaluate(payload: LicensePayload, now: Instant): LicenseValidity {
            val graceEndsAt = payload.validUntil.plus(Duration.ofDays(payload.expiryGraceDays.toLong()))
            val status = when {
                now.isBefore(payload.validFrom) -> LicenseTimeStatus.PENDING
                now.isBefore(payload.validUntil) -> LicenseTimeStatus.ACTIVE
                now.isBefore(graceEndsAt) -> LicenseTimeStatus.GRACE
                else -> LicenseTimeStatus.EXPIRED
            }
            return LicenseValidity(status, graceEndsAt)
        }
    }
}
