package uz.medclinic.clinic.identity.application

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.clinic.audit.api.AuditEvent
import uz.medclinic.clinic.audit.api.AuditRecorder
import uz.medclinic.clinic.identity.domain.PasswordPolicy
import uz.medclinic.clinic.identity.domain.StaffLogin
import uz.medclinic.clinic.identity.domain.StaffRepository
import uz.medclinic.clinic.identity.infrastructure.SessionTerminator
import uz.medclinic.clinic.identity.security.StaffPrincipal
import uz.medclinic.platform.domain.NotFoundException
import uz.medclinic.platform.domain.ValidationException
import java.time.Clock

sealed interface LoginResult {
    data class Success(val principal: StaffPrincipal) : LoginResult
    data object Failure : LoginResult
}

@Service
class AuthService(
    private val staff: StaffRepository,
    private val principals: Principals,
    private val passwordEncoder: PasswordEncoder,
    private val audit: AuditRecorder,
    private val sessions: SessionTerminator,
    private val clock: Clock,
) {
    private val dummyHash: String by lazy { passwordEncoder.encode("timing-equaliser-not-a-password")!! }

    /** Unknown login, wrong password and deactivated account are indistinguishable to the caller. */
    @Transactional
    fun authenticate(rawLogin: String, password: String): LoginResult {
        val account = staff.findByLogin(StaffLogin.normalise(rawLogin))
        if (account == null) {
            passwordEncoder.matches(password, dummyHash)
            audit.record(AuditEvent(ACTION_LOGIN_FAILED, ENTITY, null))
            return LoginResult.Failure
        }
        if (!passwordEncoder.matches(password, account.passwordHash) || !account.active) {
            audit.record(AuditEvent(ACTION_LOGIN_FAILED, ENTITY, account.id, after = mapOf("reason" to if (account.active) "bad_password" else "inactive")))
            return LoginResult.Failure
        }
        val now = clock.instant()
        staff.recordLogin(account.id, now)
        val principal = principals.of(account, now)
        audit.record(AuditEvent("auth.login_succeeded", ENTITY, account.id, actor = principal))
        return LoginResult.Success(principal)
    }

    @Transactional
    fun recordLogout(principal: StaffPrincipal) {
        audit.record(AuditEvent("auth.logout", ENTITY, principal.id, actor = principal))
    }

    @Transactional
    fun changeOwnPassword(principal: StaffPrincipal, currentPassword: String, newPassword: String, currentSessionId: String?): StaffPrincipal {
        val account = staff.findById(principal.id) ?: throw NotFoundException(ENTITY, principal.id)
        if (!passwordEncoder.matches(currentPassword, account.passwordHash)) {
            throw ValidationException("currentPassword", "validation.password_incorrect")
        }
        PasswordPolicy.validate(newPassword, field = "newPassword")
        if (passwordEncoder.matches(newPassword, account.passwordHash)) throw ValidationException("newPassword", "validation.password_reused")
        val now = clock.instant()
        val updated = staff.update(
            account.copy(passwordHash = passwordEncoder.encode(newPassword)!!, mustChangePassword = false, passwordChangedAt = now, updatedAt = now),
        )
        audit.record(AuditEvent("staff.password_changed", ENTITY, account.id, actor = principal))
        sessions.terminateAll(account.id, exceptSessionId = currentSessionId)
        return principals.of(updated, principal.authenticatedAt)
    }

    private companion object {
        const val ENTITY = "staff"
        const val ACTION_LOGIN_FAILED = "auth.login_failed"
    }
}
