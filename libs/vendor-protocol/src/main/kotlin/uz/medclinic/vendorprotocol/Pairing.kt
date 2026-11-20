package uz.medclinic.vendorprotocol

import java.util.UUID

/** Sent once by the Clinic Server installer with the code an operator typed in. */
data class PairingClaimRequest(
    val code: String,
    val hostname: String,
    val os: String,
    val appVersion: String,
)

data class PairingClaimResponse(
    val deviceId: UUID,
    val clinicId: UUID,
    val clinicCode: String,
    val branchId: UUID,
    val branchCode: String,
    /** Secret shown only once. The Clinic Server must store it encrypted at rest. */
    val deviceToken: String,
    val heartbeatIntervalSeconds: Int,
    /** Current `.lic` file content of the clinic, if a license exists. */
    val licenseFile: String?,
)
