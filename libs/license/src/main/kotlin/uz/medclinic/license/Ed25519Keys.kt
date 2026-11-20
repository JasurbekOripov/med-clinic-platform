package uz.medclinic.license

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Ed25519 key helpers using the JDK provider only. Private keys: PKCS#8 PEM; public keys: X.509 SPKI. */
object Ed25519Keys {
    private const val ALGORITHM = "Ed25519"
    private const val PRIVATE_PEM_LABEL = "PRIVATE KEY"
    private const val PUBLIC_PEM_LABEL = "PUBLIC KEY"

    fun generate(): KeyPair = KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair()

    fun privateKeyToPem(key: PrivateKey): String = pem(PRIVATE_PEM_LABEL, key.encoded)

    fun publicKeyToPem(key: PublicKey): String = pem(PUBLIC_PEM_LABEL, key.encoded)

    fun privateKeyFromPem(pem: String): PrivateKey =
        KeyFactory.getInstance(ALGORITHM).generatePrivate(PKCS8EncodedKeySpec(unpem(pem, PRIVATE_PEM_LABEL)))

    fun publicKeyFromPem(pem: String): PublicKey = publicKeyFromDer(unpem(pem, PUBLIC_PEM_LABEL))

    /** Base64 (standard) of the SPKI DER encoding — the compact form embedded in Clinic Server builds. */
    fun publicKeyToBase64(key: PublicKey): String = Base64.getEncoder().encodeToString(key.encoded)

    fun publicKeyFromBase64(base64: String): PublicKey = publicKeyFromDer(Base64.getDecoder().decode(base64.trim()))

    private fun publicKeyFromDer(der: ByteArray): PublicKey =
        KeyFactory.getInstance(ALGORITHM).generatePublic(X509EncodedKeySpec(der))

    private fun pem(label: String, der: ByteArray): String {
        val body = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der)
        return "-----BEGIN $label-----\n$body\n-----END $label-----\n"
    }

    private fun unpem(pem: String, label: String): ByteArray {
        val begin = "-----BEGIN $label-----"
        val end = "-----END $label-----"
        val text = pem.trim()
        require(text.startsWith(begin) && text.endsWith(end)) { "Expected a PEM block labelled '$label'" }
        val body = text.removePrefix(begin).removeSuffix(end).filterNot(Char::isWhitespace)
        return Base64.getDecoder().decode(body)
    }
}
