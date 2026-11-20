package uz.medclinic.vendor.licensing.api

import java.util.UUID

/** The clinic's current license as needed by other modules (pairing, heartbeat). */
class CurrentLicense(val id: UUID, val maxBranches: Int, val fileBytes: ByteArray)

/** How a license id known to a Clinic Server relates to the clinic's licenses. */
enum class LicenseRecordState { CURRENT, SUPERSEDED, REVOKED, UNKNOWN }

interface LicenseQueries {
    fun currentLicense(clinicId: UUID): CurrentLicense?

    fun stateOf(clinicId: UUID, licenseId: UUID): LicenseRecordState
}
