package uz.medclinic.vendor.identity.domain

import java.security.SecureRandom

/** One-time passwords handed to staff out of band; unambiguous characters only (no 0/O, 1/l/I). */
object TemporaryPasswords {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"
    private const val LENGTH = 14
    private val random = SecureRandom()

    fun generate(): String = buildString(LENGTH) { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
}
