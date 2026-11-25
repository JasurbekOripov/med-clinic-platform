package uz.medclinic.clinic.identity.domain

import uz.medclinic.clinic.shared.Permission
import uz.medclinic.common.UzPhone
import uz.medclinic.platform.domain.FieldViolation
import uz.medclinic.platform.domain.Text
import uz.medclinic.platform.domain.ValidationException
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

/** Editable profile of a staff member, validated and normalised. */
data class StaffProfile(val fullName: String, val position: String?, val phone: UzPhone?) {
    companion object {
        fun of(fullName: String?, position: String?, phone: String?): StaffProfile {
            val errors = mutableListOf<FieldViolation>()
            val name = Text.clean(fullName)
            if (name == null) errors += FieldViolation("fullName", "validation.not_blank")
            else if (name.length > 200) errors += FieldViolation("fullName", "validation.size", mapOf("max" to 200))
            val cleanPosition = Text.clean(position)
            if (cleanPosition != null && cleanPosition.length > 100) errors += FieldViolation("position", "validation.size", mapOf("max" to 100))
            val cleanPhone = Text.clean(phone)
            val parsedPhone = cleanPhone?.let { UzPhone.parse(it) ?: run { errors += FieldViolation("phone", "validation.phone"); null } }
            if (errors.isNotEmpty()) throw ValidationException(errors)
            return StaffProfile(name!!, cleanPosition, parsedPhone)
        }
    }
}

/** A clinic employee. Never deleted, only deactivated (history must keep their name). */
data class StaffMember(
    val id: UUID,
    val roleId: UUID,
    val login: String,
    val profile: StaffProfile,
    val passwordHash: String,
    val active: Boolean,
    val mustChangePassword: Boolean,
    val passwordChangedAt: Instant,
    val lastLoginAt: Instant?,
    val granted: Set<Permission>,
    val denied: Set<Permission>,
    val hasSignature: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Int,
) {
    /** Role permissions plus individual grants minus individual denials (system roles ignore overrides). */
    fun effectivePermissions(role: Role): Set<Permission> =
        if (role.system) role.effectivePermissions else (role.effectivePermissions + granted) - denied
}

object StaffLogin {
    private val PATTERN = Regex("^[a-z0-9][a-z0-9._-]{2,49}$")

    fun normalise(raw: String): String = raw.trim().lowercase()

    fun validate(raw: String?): String {
        val login = normalise(raw.orEmpty())
        if (!PATTERN.matches(login)) throw ValidationException("login", "validation.login_format")
        return login
    }
}

object PasswordPolicy {
    const val MIN_LENGTH = 8
    const val MAX_BYTES = 72

    fun validate(password: String, field: String = "password") {
        val tooShort = password.codePointCount(0, password.length) < MIN_LENGTH
        val tooLong = password.toByteArray(Charsets.UTF_8).size > MAX_BYTES
        if (tooShort || tooLong || password.isBlank()) {
            throw ValidationException(field, "validation.password_policy", mapOf("min" to MIN_LENGTH, "maxBytes" to MAX_BYTES))
        }
    }
}

/** One-time passwords handed over in person; unambiguous characters only. */
object TemporaryPasswords {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"
    private val random = SecureRandom()

    fun generate(length: Int = 10): String = buildString(length) { repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
}

/** PNG/JPEG only, recognised by content (not by file name), at most 512 KB. */
object SignatureImage {
    const val MAX_BYTES = 512 * 1024

    fun contentType(bytes: ByteArray): String {
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) throw ValidationException("file", "validation.image_size", mapOf("maxKb" to MAX_BYTES / 1024))
        return when {
            bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte() -> "image/png"
            bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
            else -> throw ValidationException("file", "validation.image_type")
        }
    }
}
