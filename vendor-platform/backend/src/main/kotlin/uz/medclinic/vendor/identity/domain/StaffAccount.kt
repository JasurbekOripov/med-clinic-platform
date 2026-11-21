package uz.medclinic.vendor.identity.domain

import uz.medclinic.vendor.shared.domain.ValidationException
import uz.medclinic.vendor.shared.security.Permission
import java.time.Instant
import java.util.UUID

enum class VendorRole(val permissions: Set<Permission>) {
    OWNER(Permission.entries.toSet()),
    SUPPORT(
        setOf(
            Permission.CLINIC_VIEW,
            Permission.CLINIC_EDIT,
            Permission.LICENSE_VIEW,
            Permission.DEVICE_PAIR,
            Permission.MONITORING_VIEW,
        ),
    ),
}

/** A vendor staff member. Never deleted, only deactivated. */
data class StaffAccount(
    val id: UUID,
    val login: String,
    val fullName: String,
    val role: VendorRole,
    val passwordHash: String,
    val active: Boolean,
    val mustChangePassword: Boolean,
    val passwordChangedAt: Instant,
    val lastLoginAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Int,
) {
    init {
        require(login == StaffLogin.normalise(login)) { "login must be normalised" }
        require(fullName.isNotBlank()) { "fullName is required" }
    }

    fun withProfile(fullName: String, role: VendorRole, now: Instant) = copy(fullName = fullName, role = role, updatedAt = now)

    fun deactivated(now: Instant) = copy(active = false, updatedAt = now)

    fun activated(now: Instant) = copy(active = true, updatedAt = now)

    fun withPassword(hash: String, mustChange: Boolean, now: Instant) =
        copy(passwordHash = hash, mustChangePassword = mustChange, passwordChangedAt = now, updatedAt = now)
}

object StaffLogin {
    private val PATTERN = Regex("^[a-z0-9][a-z0-9._-]{2,49}$")

    fun normalise(raw: String): String = raw.trim().lowercase()

    fun validate(raw: String): String {
        val login = normalise(raw)
        if (!PATTERN.matches(login)) throw ValidationException("login", "validation.login_format")
        return login
    }
}

object PasswordPolicy {
    const val MIN_LENGTH = 8

    /** BCrypt only uses the first 72 bytes; longer input is rejected instead of silently truncated. */
    const val MAX_BYTES = 72

    fun validate(password: String, field: String = "password") {
        val tooShort = password.codePointCount(0, password.length) < MIN_LENGTH
        val tooLong = password.toByteArray(Charsets.UTF_8).size > MAX_BYTES
        if (tooShort || tooLong || password.isBlank()) {
            throw ValidationException(field, "validation.password_policy", mapOf("min" to MIN_LENGTH, "maxBytes" to MAX_BYTES))
        }
    }
}
