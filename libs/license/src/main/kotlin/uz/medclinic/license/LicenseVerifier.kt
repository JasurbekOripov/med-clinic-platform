package uz.medclinic.license

import tools.jackson.core.JacksonException
import java.security.PublicKey
import java.security.Signature
import java.security.SignatureException
import java.util.Base64

/** Why a license file was rejected. Stable codes for logs and UI. */
enum class LicenseRejection {
    TOO_LARGE,
    MALFORMED,
    UNSUPPORTED_FORMAT,
    UNKNOWN_KEY,
    BAD_SIGNATURE,
    UNSUPPORTED_SCHEMA,
    INVALID_PAYLOAD,
    WRONG_CLINIC,
}

sealed interface LicenseVerification {
    data class Valid(val payload: LicensePayload, val keyId: String) : LicenseVerification
    data class Invalid(val reason: LicenseRejection) : LicenseVerification
}

/**
 * Offline license verification (used by the Clinic Server).
 *
 * The [keyring] (keyId → public key) must come from the application build, never from the network:
 * whoever controls the keyring controls licensing.
 */
class LicenseVerifier(keyring: Map<String, PublicKey>) {
    private val keyring: Map<String, PublicKey> = keyring.toMap()

    init {
        require(this.keyring.isNotEmpty()) { "keyring must not be empty" }
    }

    fun verify(fileBytes: ByteArray, expectedClinicId: java.util.UUID? = null): LicenseVerification {
        if (fileBytes.size > MAX_FILE_BYTES) return invalid(LicenseRejection.TOO_LARGE)

        val envelope = try {
            LicenseJson.mapper.readValue(fileBytes, LicenseEnvelope::class.java)
        } catch (_: JacksonException) {
            return invalid(LicenseRejection.MALFORMED)
        }
        if (envelope.format != LicenseEnvelope.FORMAT ||
            envelope.formatVersion != LicenseEnvelope.FORMAT_VERSION ||
            envelope.algorithm != LicenseEnvelope.ALGORITHM
        ) {
            return invalid(LicenseRejection.UNSUPPORTED_FORMAT)
        }
        val publicKey = keyring[envelope.keyId] ?: return invalid(LicenseRejection.UNKNOWN_KEY)

        val payloadBytes = decodeBase64Url(envelope.payload) ?: return invalid(LicenseRejection.MALFORMED)
        val signature = decodeBase64Url(envelope.signature) ?: return invalid(LicenseRejection.MALFORMED)
        if (signature.size != ED25519_SIGNATURE_BYTES || !signatureMatches(publicKey, payloadBytes, signature)) {
            return invalid(LicenseRejection.BAD_SIGNATURE)
        }

        // Only signed bytes are parsed from here on.
        val schema = try {
            LicenseJson.mapper.readTree(payloadBytes).path("schema").asInt(0)
        } catch (_: JacksonException) {
            return invalid(LicenseRejection.INVALID_PAYLOAD)
        }
        if (schema != LicensePayload.CURRENT_SCHEMA) return invalid(LicenseRejection.UNSUPPORTED_SCHEMA)

        val payload = try {
            LicenseJson.mapper.readValue(payloadBytes, LicensePayload::class.java)
        } catch (_: JacksonException) {
            return invalid(LicenseRejection.INVALID_PAYLOAD)
        } catch (_: IllegalArgumentException) {
            return invalid(LicenseRejection.INVALID_PAYLOAD)
        }
        if (expectedClinicId != null && payload.clinicId != expectedClinicId) {
            return invalid(LicenseRejection.WRONG_CLINIC)
        }
        return LicenseVerification.Valid(payload, envelope.keyId)
    }

    private fun signatureMatches(key: PublicKey, data: ByteArray, signature: ByteArray): Boolean = try {
        Signature.getInstance(LicenseEnvelope.ALGORITHM).run {
            initVerify(key)
            update(data)
            verify(signature)
        }
    } catch (_: SignatureException) {
        false
    }

    private fun decodeBase64Url(value: String): ByteArray? = try {
        Base64.getUrlDecoder().decode(value)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun invalid(reason: LicenseRejection) = LicenseVerification.Invalid(reason)

    companion object {
        const val MAX_FILE_BYTES = 64 * 1024
        private const val ED25519_SIGNATURE_BYTES = 64
    }
}
