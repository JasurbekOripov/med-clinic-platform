package uz.medclinic.vendor.licensing.infrastructure

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import uz.medclinic.license.Ed25519Keys
import uz.medclinic.license.LicenseSigner
import uz.medclinic.license.LicenseVerifier
import uz.medclinic.license.LicenseVerification
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.PublicKey
import java.time.Instant
import java.util.UUID

@ConfigurationProperties("vendor.licensing")
data class LicensingProperties(val signing: Signing = Signing()) {
    data class Signing(
        /** Identifier embedded in every license; Clinic Server builds map it to the public key. */
        val keyId: String = "vk-dev-1",
        /** PKCS#8 PEM private key. The public key is expected next to it as `<path>.pub` (SPKI PEM). */
        val privateKeyPath: String = "",
        /** Development only: create a key pair when the files are missing. Never enable in production. */
        val generateIfMissing: Boolean = false,
    )
}

/**
 * Loads the vendor's Ed25519 license signing key. Fails fast at startup when the key is missing or does not
 * match its public key, so a misconfigured server never issues unverifiable licenses.
 */
@Component
class SigningKeys(properties: LicensingProperties) {
    private val log = LoggerFactory.getLogger(javaClass)

    val keyId: String = properties.signing.keyId
    final val signer: LicenseSigner
    final val publicKey: PublicKey

    init {
        val signing = properties.signing
        require(signing.privateKeyPath.isNotBlank()) { "vendor.licensing.signing.private-key-path must be set" }
        val privatePath = Path.of(signing.privateKeyPath)
        val publicPath = Path.of(signing.privateKeyPath + ".pub")
        if (!Files.exists(privatePath) && signing.generateIfMissing) generate(privatePath, publicPath)
        check(Files.isReadable(privatePath) && Files.isReadable(publicPath)) {
            "License signing key files not found or not readable: $privatePath(.pub)"
        }
        signer = LicenseSigner(keyId, Ed25519Keys.privateKeyFromPem(Files.readString(privatePath)))
        publicKey = Ed25519Keys.publicKeyFromPem(Files.readString(publicPath))
        selfTest()
    }

    private fun selfTest() {
        val probe = uz.medclinic.license.LicensePayload(
            licenseId = UUID(0, 1), serial = 1, clinicId = UUID(0, 2), clinicCode = "SELFTEST", clinicName = "self-test",
            plan = "SELFTEST", modules = emptyList(), maxUsers = 1, maxBranches = 1,
            validFrom = Instant.EPOCH, validUntil = Instant.EPOCH.plusSeconds(1), expiryGraceDays = 0, issuedAt = Instant.EPOCH,
        )
        val result = LicenseVerifier(mapOf(keyId to publicKey)).verify(signer.sign(probe).fileBytes())
        check(result is LicenseVerification.Valid) { "License signing key does not match its public key ($result)" }
    }

    private fun generate(privatePath: Path, publicPath: Path) {
        log.warn("Generating a NEW license signing key pair at {} (development only)", privatePath)
        val pair = Ed25519Keys.generate()
        privatePath.parent?.let(Files::createDirectories)
        Files.writeString(privatePath, Ed25519Keys.privateKeyToPem(pair.private))
        Files.writeString(publicPath, Ed25519Keys.publicKeyToPem(pair.public))
        runCatching { Files.setPosixFilePermissions(privatePath, PosixFilePermissions.fromString("rw-------")) }
    }
}
