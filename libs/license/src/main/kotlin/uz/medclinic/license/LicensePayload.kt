package uz.medclinic.license

import java.time.Instant
import java.util.UUID

/**
 * The signed content of a license. Immutable; whatever is signed is exactly what the Clinic Server sees.
 *
 * Forward compatibility: verifiers ignore unknown JSON fields and unknown module codes, so newer vendor
 * versions can add data without breaking older Clinic Servers. Incompatible changes bump [schema].
 */
data class LicensePayload(
    val schema: Int = CURRENT_SCHEMA,
    val licenseId: UUID,
    val serial: Long,
    val clinicId: UUID,
    val clinicCode: String,
    val clinicName: String,
    val plan: String,
    val modules: List<String>,
    val maxUsers: Int,
    val maxBranches: Int,
    val validFrom: Instant,
    val validUntil: Instant,
    val expiryGraceDays: Int,
    val issuedAt: Instant,
) {
    init {
        require(schema >= 1) { "schema must be positive" }
        require(serial > 0) { "serial must be positive" }
        require(clinicCode.isNotBlank()) { "clinicCode is required" }
        require(clinicName.isNotBlank()) { "clinicName is required" }
        require(plan.isNotBlank()) { "plan is required" }
        require(maxUsers > 0) { "maxUsers must be positive" }
        require(maxBranches > 0) { "maxBranches must be positive" }
        require(validUntil.isAfter(validFrom)) { "validUntil must be after validFrom" }
        require(expiryGraceDays in 0..MAX_GRACE_DAYS) { "expiryGraceDays must be in 0..$MAX_GRACE_DAYS" }
        require(modules.size == modules.toSet().size) { "modules must not contain duplicates" }
    }

    fun hasModule(module: String): Boolean = module in modules

    companion object {
        const val CURRENT_SCHEMA = 1
        const val MAX_GRACE_DAYS = 90
    }
}

/** Module codes known to this library version. Licenses may carry others; consumers ignore them. */
object LicenseModules {
    const val REGISTRATION = "REGISTRATION"
    const val QUEUE = "QUEUE"
    const val CASHIER = "CASHIER"
    const val LAB = "LAB"
    const val DOCTOR = "DOCTOR"
    const val INPATIENT = "INPATIENT"
    const val OPERATIONS = "OPERATIONS"
    const val REPORTS = "REPORTS"
    const val TELEGRAM = "TELEGRAM"

    val ALL: List<String> = listOf(REGISTRATION, QUEUE, CASHIER, LAB, DOCTOR, INPATIENT, OPERATIONS, REPORTS, TELEGRAM)
}
