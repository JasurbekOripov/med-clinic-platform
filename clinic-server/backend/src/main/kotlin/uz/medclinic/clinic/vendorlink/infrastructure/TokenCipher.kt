package uz.medclinic.clinic.vendorlink.infrastructure

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uz.medclinic.clinic.vendorlink.VendorLinkProperties
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts the vendor device token at rest with AES-256-GCM. The key lives in a local file next to the data,
 * so a database dump alone does not reveal the token.
 * ASSUMPTION: on Windows the installer milestone will protect this key with DPAPI and NTFS permissions.
 */
@Component
class TokenCipher(properties: VendorLinkProperties) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()
    private val key: SecretKey = loadOrCreate(Path.of(properties.secretKeyPath))

    fun encrypt(plain: String): ByteArray {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }
        return iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
    }

    fun decrypt(data: ByteArray): String {
        require(data.size > IV_BYTES) { "ciphertext too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, data.copyOfRange(0, IV_BYTES)))
        }
        return String(cipher.doFinal(data.copyOfRange(IV_BYTES, data.size)), Charsets.UTF_8)
    }

    private fun loadOrCreate(path: Path): SecretKey {
        if (!Files.exists(path)) {
            log.info("Creating a new local secret key at {}", path.toAbsolutePath())
            path.toAbsolutePath().parent?.let(Files::createDirectories)
            Files.write(path, ByteArray(KEY_BYTES).also(random::nextBytes))
            runCatching { Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------")) }
        }
        val bytes = Files.readAllBytes(path)
        check(bytes.size == KEY_BYTES) { "Secret key file $path is corrupt" }
        return SecretKeySpec(bytes, "AES")
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BYTES = 32
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
