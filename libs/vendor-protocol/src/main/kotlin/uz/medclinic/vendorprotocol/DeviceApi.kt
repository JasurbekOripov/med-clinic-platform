package uz.medclinic.vendorprotocol

/** Paths and constants of the device API (Clinic Server → Vendor Platform, outgoing only). */
object DeviceApi {
    const val BASE_PATH = "/api/device/v1"
    const val PAIRING_CLAIM_PATH = "$BASE_PATH/pairing/claim"
    const val HEARTBEATS_PATH = "$BASE_PATH/heartbeats"
    const val LICENSE_PATH = "$BASE_PATH/license"

    /** Device tokens look like `dvt1_<43 base64url chars>`; the prefix makes leaked tokens easy to detect. */
    const val TOKEN_PREFIX = "dvt1_"
    const val AUTHORIZATION_SCHEME = "Bearer"
}
