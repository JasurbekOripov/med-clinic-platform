package uz.medclinic.license

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Base64
import java.util.UUID

class LicenseVerifierTest {
    private val keys = Ed25519Keys.generate()
    private val signer = LicenseSigner("vk-test-1", keys.private)
    private val verifier = LicenseVerifier(mapOf("vk-test-1" to keys.public))

    @Test
    fun `round trip sign and verify returns the payload with second precision`() {
        val file = signer.sign(LicenseFixtures.payload()).fileBytes()

        val result = verifier.verify(file, expectedClinicId = LicenseFixtures.CLINIC_ID)

        assertThat(result).isInstanceOf(LicenseVerification.Valid::class.java)
        val payload = (result as LicenseVerification.Valid).payload
        assertThat(payload.clinicCode).isEqualTo("CL-0001")
        assertThat(payload.modules).containsExactly(LicenseModules.REGISTRATION, LicenseModules.CASHIER)
        assertThat(payload.issuedAt).isEqualTo(Instant.parse("2026-01-01T10:15:30Z"))
        assertThat(result.keyId).isEqualTo("vk-test-1")
    }

    @Test
    fun `file bytes are deterministic for the same signed payload`() {
        val signed = signer.sign(LicenseFixtures.payload())
        val again = SignedLicense(signed.keyId, signed.payloadBytes, signed.signature)

        assertThat(again.fileBytes()).isEqualTo(signed.fileBytes())
    }

    @Test
    fun `tampered payload is rejected`() {
        val signed = signer.sign(LicenseFixtures.payload())
        val tamperedJson = String(signed.payloadBytes).replace("\"maxUsers\":25", "\"maxUsers\":999")
        val tampered = signed.envelope.copy(payload = b64(tamperedJson.toByteArray()))

        assertThat(verifier.verify(write(tampered)))
            .isEqualTo(LicenseVerification.Invalid(LicenseRejection.BAD_SIGNATURE))
    }

    @Test
    fun `signature from another key is rejected even under a known key id`() {
        val foreign = LicenseSigner("vk-test-1", Ed25519Keys.generate().private).sign(LicenseFixtures.payload())

        assertThat(verifier.verify(foreign.fileBytes()))
            .isEqualTo(LicenseVerification.Invalid(LicenseRejection.BAD_SIGNATURE))
    }

    @Test
    fun `unknown key id is rejected`() {
        val other = LicenseSigner("vk-other", keys.private).sign(LicenseFixtures.payload())

        assertThat(verifier.verify(other.fileBytes()))
            .isEqualTo(LicenseVerification.Invalid(LicenseRejection.UNKNOWN_KEY))
    }

    @Test
    fun `license of another clinic is rejected`() {
        val file = signer.sign(LicenseFixtures.payload()).fileBytes()

        assertThat(verifier.verify(file, expectedClinicId = UUID.randomUUID()))
            .isEqualTo(LicenseVerification.Invalid(LicenseRejection.WRONG_CLINIC))
    }

    @Test
    fun `garbage, wrong format and oversized files are rejected`() {
        val signed = signer.sign(LicenseFixtures.payload())

        assertThat(verifier.verify("not json".toByteArray()))
            .isEqualTo(LicenseVerification.Invalid(LicenseRejection.MALFORMED))
        assertThat(verifier.verify(write(signed.envelope.copy(format = "other"))))
            .isEqualTo(LicenseVerification.Invalid(LicenseRejection.UNSUPPORTED_FORMAT))
        assertThat(verifier.verify(write(signed.envelope.copy(signature = "%%%"))))
            .isEqualTo(LicenseVerification.Invalid(LicenseRejection.MALFORMED))
        assertThat(verifier.verify(ByteArray(LicenseVerifier.MAX_FILE_BYTES + 1)))
            .isEqualTo(LicenseVerification.Invalid(LicenseRejection.TOO_LARGE))
    }

    @Test
    fun `signed payload with a future schema is rejected as unsupported`() {
        val json = String(signer.sign(LicenseFixtures.payload()).payloadBytes).replace("\"schema\":1", "\"schema\":2")
        val resigned = signRaw(json.toByteArray())

        assertThat(verifier.verify(resigned))
            .isEqualTo(LicenseVerification.Invalid(LicenseRejection.UNSUPPORTED_SCHEMA))
    }

    @Test
    fun `unknown payload fields and module codes are tolerated`() {
        val json = String(signer.sign(LicenseFixtures.payload(modules = listOf("FUTURE_MODULE"))).payloadBytes)
            .replace("\"schema\":1,", "\"schema\":1,\"newField\":{\"x\":1},")

        val result = verifier.verify(signRaw(json.toByteArray()))

        assertThat(result).isInstanceOf(LicenseVerification.Valid::class.java)
        assertThat((result as LicenseVerification.Valid).payload.hasModule("FUTURE_MODULE")).isTrue()
    }

    @Test
    fun `validly signed but semantically invalid payload is rejected`() {
        val json = String(signer.sign(LicenseFixtures.payload()).payloadBytes).replace("\"maxUsers\":25", "\"maxUsers\":0")

        assertThat(verifier.verify(signRaw(json.toByteArray())))
            .isEqualTo(LicenseVerification.Invalid(LicenseRejection.INVALID_PAYLOAD))
    }

    @Test
    fun `keys survive PEM and base64 round trips`() {
        val private = Ed25519Keys.privateKeyFromPem(Ed25519Keys.privateKeyToPem(keys.private))
        val public = Ed25519Keys.publicKeyFromBase64(Ed25519Keys.publicKeyToBase64(keys.public))
        val publicFromPem = Ed25519Keys.publicKeyFromPem(Ed25519Keys.publicKeyToPem(keys.public))
        val file = LicenseSigner("vk-test-1", private).sign(LicenseFixtures.payload()).fileBytes()

        assertThat(LicenseVerifier(mapOf("vk-test-1" to public)).verify(file)).isInstanceOf(LicenseVerification.Valid::class.java)
        assertThat(publicFromPem.encoded).isEqualTo(keys.public.encoded)
    }

    private fun signRaw(payloadBytes: ByteArray): ByteArray {
        val signature = java.security.Signature.getInstance("Ed25519").run {
            initSign(keys.private); update(payloadBytes); sign()
        }
        return SignedLicense("vk-test-1", payloadBytes, signature).fileBytes()
    }

    private fun write(envelope: LicenseEnvelope): ByteArray = LicenseJson.mapper.writeValueAsBytes(envelope)

    private fun b64(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
