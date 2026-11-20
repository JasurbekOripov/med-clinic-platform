package uz.medclinic.license

import java.security.PrivateKey
import java.security.Signature
import java.time.temporal.ChronoUnit
import java.util.Base64

/** Result of signing: the stored artefacts plus the downloadable file. */
class SignedLicense(
    val keyId: String,
    val payloadBytes: ByteArray,
    val signature: ByteArray,
) {
    val envelope: LicenseEnvelope = LicenseEnvelope(
        format = LicenseEnvelope.FORMAT,
        formatVersion = LicenseEnvelope.FORMAT_VERSION,
        algorithm = LicenseEnvelope.ALGORITHM,
        keyId = keyId,
        payload = BASE64URL.encodeToString(payloadBytes),
        signature = BASE64URL.encodeToString(signature),
    )

    /** The `.lic` file bytes. Deterministic for the same stored payload/signature. */
    fun fileBytes(): ByteArray = LicenseJson.mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(envelope)

    private companion object {
        val BASE64URL: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    }
}

/** Signs license payloads with the vendor's Ed25519 private key (Vendor Platform only). */
class LicenseSigner(private val keyId: String, private val privateKey: PrivateKey) {
    init {
        require(keyId.matches(KEY_ID_PATTERN)) { "keyId must match $KEY_ID_PATTERN" }
        require(privateKey.algorithm in ED25519_NAMES) { "An Ed25519 private key is required" }
    }

    fun sign(payload: LicensePayload): SignedLicense {
        val normalised = payload.copy(
            validFrom = payload.validFrom.truncatedTo(ChronoUnit.SECONDS),
            validUntil = payload.validUntil.truncatedTo(ChronoUnit.SECONDS),
            issuedAt = payload.issuedAt.truncatedTo(ChronoUnit.SECONDS),
        )
        val payloadBytes = LicenseJson.mapper.writeValueAsBytes(normalised)
        val signature = Signature.getInstance(LicenseEnvelope.ALGORITHM).run {
            initSign(privateKey)
            update(payloadBytes)
            sign()
        }
        return SignedLicense(keyId, payloadBytes, signature)
    }

    companion object {
        val KEY_ID_PATTERN = Regex("^[a-z0-9][a-z0-9.-]{1,40}$")
        private val ED25519_NAMES = setOf("Ed25519", "EdDSA")
    }
}
