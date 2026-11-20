package uz.medclinic.vendor.identity.application

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.common.Ids
import uz.medclinic.vendor.audit.api.AuditEvent
import uz.medclinic.vendor.audit.api.AuditRecorder
import uz.medclinic.vendor.identity.domain.PasswordPolicy
import uz.medclinic.vendor.identity.domain.StaffAccount
import uz.medclinic.vendor.identity.domain.StaffLogin
import uz.medclinic.vendor.identity.domain.StaffRepository
import uz.medclinic.vendor.identity.domain.TemporaryPasswords
import uz.medclinic.vendor.identity.domain.VendorRole
import uz.medclinic.vendor.identity.infrastructure.SessionTerminator
import uz.medclinic.vendor.shared.domain.DomainException
import uz.medclinic.vendor.shared.domain.NotFoundException
import uz.medclinic.vendor.shared.domain.Page
import uz.medclinic.vendor.shared.domain.PageQuery
import uz.medclinic.vendor.shared.domain.Text
import uz.medclinic.vendor.shared.domain.ValidationException
import uz.medclinic.vendor.shared.domain.VersionConflictException
import uz.medclinic.vendor.shared.security.CurrentActor
import java.time.Clock
import java.util.UUID

data class CreateStaffCommand(val login: String, val fullName: String, val role: VendorRole)

data class UpdateStaffCommand(val id: UUID, val fullName: String, val role: VendorRole, val version: Int)

/** A staff account plus a one-time password that is shown to the operator exactly once. */
data class StaffWithTemporaryPassword(val staff: StaffAccount, val temporaryPassword: String)

/** Audit snapshot: never contains the password hash. */
private data class StaffSnapshot(val login: String, val fullName: String, val role: VendorRole, val active: Boolean, val mustChangePassword: Boolean) {
    constructor(s: StaffAccount) : this(s.login, s.fullName, s.role, s.active, s.mustChangePassword)
}

@Service
class StaffService(
    private val staff: StaffRepository,
    private val passwordEncoder: PasswordEncoder,
    private val audit: AuditRecorder,
    private val sessions: SessionTerminator,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun search(query: String?, includeInactive: Boolean, page: PageQuery): Page<StaffAccount> =
        staff.search(Text.clean(query), includeInactive, page)

    @Transactional(readOnly = true)
    fun get(id: UUID): StaffAccount = staff.findById(id) ?: throw NotFoundException(ENTITY, id)

    @Transactional
    fun create(command: CreateStaffCommand): StaffWithTemporaryPassword {
        val temporaryPassword = TemporaryPasswords.generate()
        val created = createAccount(command, temporaryPassword, mustChangePassword = true)
        return StaffWithTemporaryPassword(created, temporaryPassword)
    }

    /** Used by the first-run bootstrap; the password comes from deployment configuration. */
    @Transactional
    fun createInitialOwner(login: String, fullName: String, password: String): StaffAccount? {
        if (staff.count() > 0) return null
        PasswordPolicy.validate(password)
        return createAccount(CreateStaffCommand(login, fullName, VendorRole.OWNER), password, mustChangePassword = true)
    }

    @Transactional
    fun update(command: UpdateStaffCommand): StaffAccount {
        val current = load(command.id, command.version)
        val fullName = validFullName(command.fullName)
        if (current.role == VendorRole.OWNER && command.role != VendorRole.OWNER) ensureAnotherActiveOwner(current)
        val updated = staff.update(current.withProfile(fullName, command.role, clock.instant()))
        audit.record(AuditEvent("staff.updated", ENTITY, current.id, before = StaffSnapshot(current), after = StaffSnapshot(updated)))
        if (current.role != updated.role) sessions.terminateAll(current.id)
        return updated
    }

    @Transactional
    fun deactivate(id: UUID, version: Int): StaffAccount {
        val current = load(id, version)
        if (id == CurrentActor.staffId()) throw DomainException("staff.cannot_deactivate_self")
        if (!current.active) return current
        if (current.role == VendorRole.OWNER) ensureAnotherActiveOwner(current)
        val updated = staff.update(current.deactivated(clock.instant()))
        audit.record(AuditEvent("staff.deactivated", ENTITY, id, before = StaffSnapshot(current), after = StaffSnapshot(updated)))
        sessions.terminateAll(id)
        return updated
    }

    @Transactional
    fun activate(id: UUID, version: Int): StaffAccount {
        val current = load(id, version)
        if (current.active) return current
        val updated = staff.update(current.activated(clock.instant()))
        audit.record(AuditEvent("staff.activated", ENTITY, id, before = StaffSnapshot(current), after = StaffSnapshot(updated)))
        return updated
    }

    @Transactional
    fun resetPassword(id: UUID, version: Int): StaffWithTemporaryPassword {
        val current = load(id, version)
        if (id == CurrentActor.staffId()) throw DomainException("staff.use_change_password")
        val temporaryPassword = TemporaryPasswords.generate()
        val updated = staff.update(current.withPassword(passwordEncoder.encode(temporaryPassword)!!, mustChange = true, now = clock.instant()))
        audit.record(AuditEvent("staff.password_reset", ENTITY, id))
        sessions.terminateAll(id)
        return StaffWithTemporaryPassword(updated, temporaryPassword)
    }

    private fun createAccount(command: CreateStaffCommand, password: String, mustChangePassword: Boolean): StaffAccount {
        val now = clock.instant()
        val account = StaffAccount(
            id = Ids.newId(),
            login = StaffLogin.validate(command.login),
            fullName = validFullName(command.fullName),
            role = command.role,
            passwordHash = passwordEncoder.encode(password)!!,
            active = true,
            mustChangePassword = mustChangePassword,
            passwordChangedAt = now,
            lastLoginAt = null,
            createdAt = now,
            updatedAt = now,
            version = 0,
        )
        staff.insert(account)
        audit.record(AuditEvent("staff.created", ENTITY, account.id, after = StaffSnapshot(account)))
        return account
    }

    private fun load(id: UUID, expectedVersion: Int): StaffAccount {
        val account = staff.findById(id) ?: throw NotFoundException(ENTITY, id)
        if (account.version != expectedVersion) throw VersionConflictException(ENTITY, id)
        return account
    }

    private fun ensureAnotherActiveOwner(owner: StaffAccount) {
        staff.lockOwners()
        val remaining = staff.countActiveOwners() - if (owner.active) 1 else 0
        if (remaining < 1) throw DomainException("staff.last_owner")
    }

    private fun validFullName(raw: String): String {
        val name = Text.clean(raw) ?: throw ValidationException("fullName", "validation.not_blank")
        if (name.length > MAX_NAME) throw ValidationException("fullName", "validation.size", mapOf("max" to MAX_NAME))
        return name
    }

    private companion object {
        const val ENTITY = "staff"
        const val MAX_NAME = 200
    }
}
