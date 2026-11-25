package uz.medclinic.clinic.support

import uz.medclinic.license.Ed25519Keys
import uz.medclinic.license.LicenseModules
import uz.medclinic.license.LicensePayload
import uz.medclinic.license.LicenseSigner
import java.time.Instant
import java.util.UUID

/** Signs licenses with the test-only vendor key whose public half is configured in application-test.yml. */
object TestLicenses {
    private val signer: LicenseSigner by lazy {
        val pem = TestLicenses::class.java.getResource("/license/test-vendor-private-key.TEST-ONLY.txt")!!.readText()
        LicenseSigner("vk-test-1", Ed25519Keys.privateKeyFromPem(pem))
    }

    /** A foreign key the clinic does not trust (for negative tests). */
    val untrustedSigner = LicenseSigner("vk-test-1", Ed25519Keys.generate().private)

    fun payload(
        clinicId: UUID,
        serial: Long = 1,
        validFrom: Instant = Instant.parse("2026-01-01T00:00:00Z"),
        validUntil: Instant = Instant.parse("2027-01-01T00:00:00Z"),
        graceDays: Int = 14,
        maxUsers: Int = 50,
        licenseId: UUID = UUID.randomUUID(),
    ) = LicensePayload(
        licenseId = licenseId, serial = serial, clinicId = clinicId, clinicCode = "CL-0001", clinicName = "Shifo Med",
        plan = "STANDARD", modules = LicenseModules.ALL, maxUsers = maxUsers, maxBranches = 1, validFrom = validFrom,
        validUntil = validUntil, expiryGraceDays = graceDays, issuedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    fun file(payload: LicensePayload, signer: LicenseSigner = this.signer): ByteArray = signer.sign(payload).fileBytes()
}
