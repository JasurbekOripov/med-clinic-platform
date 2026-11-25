package uz.medclinic.vendor.identity.application

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.vendor.audit.api.AuditEvent
import uz.medclinic.vendor.audit.api.AuditRecorder
import uz.medclinic.vendor.identity.domain.PasswordPolicy
import uz.medclinic.vendor.identity.domain.StaffLogin
import uz.medclinic.vendor.identity.domain.StaffRepository
import uz.medclinic.vendor.identity.infrastructure.SessionTerminator
import uz.medclinic.vendor.identity.security.StaffPrincipal
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
    private val passwordEncoder: PasswordEncoder,
    private val audit: AuditRecorder,
    private val sessions: SessionTerminator,
    private val clock: Clock,
) {
    /** Compared against when the login does not exist, so response time does not reveal valid logins. */
    private val dummyHash: String by lazy { passwordEncoder.encode("timing-equaliser-not-a-password")!! }

    /**
     * Returns a result instead of throwing so that the failure audit row is committed.
     * Unknown login, wrong password and deactivated account are indistinguishable to the caller.
     */
    @Transactional
    fun authenticate(rawLogin: String, password: String): LoginResult {
        val account = staff.findByLogin(StaffLogin.normalise(rawLogin))
        if (account == null) {
            passwordEncoder.matches(password, dummyHash)
            // The attempted login is not stored: users sometimes type their password into the login field.
            audit.record(AuditEvent(action = ACTION_LOGIN_FAILED, entityType = ENTITY, entityId = null))
            return LoginResult.Failure
        }
        if (!passwordEncoder.matches(password, account.passwordHash) || !account.active) {
            audit.record(
                AuditEvent(
                    action = ACTION_LOGIN_FAILED,
                    entityType = ENTITY,
                    entityId = account.id,
                    after = mapOf("reason" to if (account.active) "bad_password" else "inactive"),
                ),
            )
            return LoginResult.Failure
        }
        val now = clock.instant()
        staff.recordLogin(account.id, now)
        val principal = StaffPrincipal.of(account, now)
        audit.record(AuditEvent(action = ACTION_LOGIN_SUCCEEDED, entityType = ENTITY, entityId = account.id, actor = principal))
        return LoginResult.Success(principal)
    }

    @Transactional
    fun recordLogout(principal: StaffPrincipal) {
        audit.record(AuditEvent(action = "auth.logout", entityType = ENTITY, entityId = principal.id, actor = principal))
    }

    /** Changes the caller's own password and ends their other sessions. Returns the refreshed principal. */
    @Transactional
    fun changeOwnPassword(principal: StaffPrincipal, currentPassword: String, newPassword: String, currentSessionId: String?): StaffPrincipal {
        val account = staff.findById(principal.id) ?: throw NotFoundException(ENTITY, principal.id)
        if (!passwordEncoder.matches(currentPassword, account.passwordHash)) {
            throw ValidationException("currentPassword", "validation.password_incorrect")
        }
        PasswordPolicy.validate(newPassword, field = "newPassword")
        if (passwordEncoder.matches(newPassword, account.passwordHash)) {
            throw ValidationException("newPassword", "validation.password_reused")
        }
        val now = clock.instant()
        val updated = staff.update(account.withPassword(passwordEncoder.encode(newPassword)!!, mustChange = false, now = now))
        audit.record(AuditEvent(action = "staff.password_changed", entityType = ENTITY, entityId = account.id, actor = principal))
        sessions.terminateAll(account.id, exceptSessionId = currentSessionId)
        return StaffPrincipal.of(updated, principal.authenticatedAt)
    }

    companion object {
        const val ENTITY = "staff"
        const val ACTION_LOGIN_SUCCEEDED = "auth.login_succeeded"
        const val ACTION_LOGIN_FAILED = "auth.login_failed"
    }
}
