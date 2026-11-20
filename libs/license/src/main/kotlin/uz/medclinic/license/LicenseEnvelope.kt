package uz.medclinic.license

/**
 * The `.lic` file. The signature covers the exact (base64url-decoded) [payload] bytes, so neither side
 * has to canonicalise JSON.
 */
data class LicenseEnvelope(
    val format: String,
    val formatVersion: Int,
    val algorithm: String,
    val keyId: String,
    val payload: String,
    val signature: String,
) {
    companion object {
        const val FORMAT = "medclinic.license"
        const val FORMAT_VERSION = 1
        const val ALGORITHM = "Ed25519"
        const val FILE_EXTENSION = "lic"
        const val MEDIA_TYPE = "application/vnd.medclinic.license+json"
    }
}
