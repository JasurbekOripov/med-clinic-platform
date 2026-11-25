package uz.medclinic.clinic.vendorlink.application

import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Component
import uz.medclinic.clinic.vendorlink.VendorLinkProperties
import uz.medclinic.license.Ed25519Keys
import uz.medclinic.license.LicenseVerifier
import java.security.PublicKey
import java.util.Properties

/**
 * Trusted license keys: baked into the build (`license/keyring.properties`); in dev/test profiles only, extra keys
 * from `clinic.vendor.dev-keyring`.
 * ASSUMPTION/limit: on-premise licensing deters casual misuse; someone with full control of the server could
 * still patch the program. That is accepted for this product.
 */
@Component
class Keyring(properties: VendorLinkProperties, environment: Environment) {
    private val log = LoggerFactory.getLogger(javaClass)

    val keys: Map<String, PublicKey> = buildMap {
        putAll(parse(bakedKeys()))
        if (environment.acceptsProfiles(Profiles.of("dev", "test")) && properties.devKeyring.isNotBlank()) {
            putAll(parse(properties.devKeyring.split(',').mapNotNull { e -> e.split(':', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }))
        }
    }

    init {
        if (keys.isEmpty()) log.warn("No license public keys configured: no license can be verified")
    }

    /** Null when no key is configured (then every license is reported invalid). */
    fun verifier(): LicenseVerifier? = keys.takeIf { it.isNotEmpty() }?.let(::LicenseVerifier)

    private fun bakedKeys(): List<Pair<String, String>> {
        val stream = javaClass.getResourceAsStream("/license/keyring.properties") ?: return emptyList()
        val props = Properties().apply { stream.use { load(it) } }
        return props.stringPropertyNames().map { it to props.getProperty(it) }
    }

    private fun parse(entries: List<Pair<String, String>>): Map<String, PublicKey> = entries
        .filter { (id, key) -> id.isNotBlank() && key.isNotBlank() }
        .associate { (id, key) -> id.trim() to Ed25519Keys.publicKeyFromBase64(key.trim()) }
}
