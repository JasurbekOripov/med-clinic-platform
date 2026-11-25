package uz.medclinic.clinic.identity.application

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.clinic.audit.api.AuditEvent
import uz.medclinic.clinic.audit.api.AuditRecorder
import uz.medclinic.clinic.identity.domain.Role
import uz.medclinic.clinic.identity.domain.RoleRepository
import uz.medclinic.clinic.identity.domain.RoleTemplate
import uz.medclinic.clinic.identity.domain.SignatureImage
import uz.medclinic.clinic.identity.domain.StaffFilter
import uz.medclinic.clinic.identity.domain.StaffLogin
import uz.medclinic.clinic.identity.domain.StaffMember
import uz.medclinic.clinic.identity.domain.StaffProfile
import uz.medclinic.clinic.identity.domain.StaffRepository
import uz.medclinic.clinic.identity.domain.TemporaryPasswords
import uz.medclinic.clinic.identity.domain.parsePermissions
import uz.medclinic.clinic.identity.infrastructure.SessionTerminator
import uz.medclinic.clinic.shared.LicenseLimits
import uz.medclinic.common.Ids
import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.domain.NotFoundException
import uz.medclinic.platform.domain.Page
import uz.medclinic.platform.domain.PageQuery
import uz.medclinic.platform.domain.Text
import uz.medclinic.platform.domain.ValidationException
import uz.medclinic.platform.domain.VersionConflictException
import uz.medclinic.platform.security.CurrentActor
import java.time.Clock
import java.util.UUID

data class StaffCommand(
    val fullName: String?,
    val position: String?,
    val phone: String?,
    val roleId: UUID,
    val granted: List<String> = emptyList(),
    val denied: List<String> = emptyList(),
)

data class StaffWithTemporaryPassword(val staff: StaffMember, val temporaryPassword: String)

/** Audit snapshot: never contains the password hash or the signature image. */
private data class StaffSnapshot(
    val login: String,
    val fullName: String,
    val position: String?,
    val phone: String?,
    val roleId: UUID,
    val active: Boolean,
    val granted: List<String>,
    val denied: List<String>,
) {
    constructor(s: StaffMember) : this(
        s.login, s.profile.fullName, s.profile.position, s.profile.phone?.e164, s.roleId, s.active,
        s.granted.map { it.name }.sorted(), s.denied.map { it.name }.sorted(),
    )
}

@Service
class StaffService(
    private val staff: StaffRepository,
    private val roles: RoleRepository,
    private val passwordEncoder: PasswordEncoder,
    private val audit: AuditRecorder,
    private val sessions: SessionTerminator,
    private val license: LicenseLimits,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun search(filter: StaffFilter, page: PageQuery): Page<StaffMember> = staff.search(filter.copy(query = Text.clean(filter.query)), page)

    @Transactional(readOnly = true)
    fun get(id: UUID): StaffMember = staff.findById(id) ?: throw NotFoundException(ENTITY, id)

    @Transactional
    fun create(login: String?, command: StaffCommand): StaffWithTemporaryPassword {
        val role = assignableRole(command.roleId)
        requireLicenseSeat()
        val password = TemporaryPasswords.generate()
        val created = insert(StaffLogin.validate(login), command, role, passwordEncoder.encode(password)!!, mustChange = true)
        return StaffWithTemporaryPassword(created, password)
    }

    /** Internal creation used by setup and importers (role already resolved, password chosen by the caller). */
    internal fun insert(login: String, command: StaffCommand, role: Role, passwordHash: String, mustChange: Boolean): StaffMember {
        val now = clock.instant()
        val member = StaffMember(
            id = Ids.newId(),
            roleId = role.id,
            login = login,
            profile = StaffProfile.of(command.fullName, command.position, command.phone),
            passwordHash = passwordHash,
            active = true,
            mustChangePassword = mustChange,
            passwordChangedAt = now,
            lastLoginAt = null,
            granted = if (role.system) emptySet() else parsePermissions(command.granted, "granted"),
            denied = if (role.system) emptySet() else parsePermissions(command.denied, "denied"),
            hasSignature = false,
            createdAt = now,
            updatedAt = now,
            version = 0,
        )
        staff.insert(member)
        audit.record(AuditEvent("staff.created", ENTITY, member.id, after = StaffSnapshot(member)))
        return member
    }

    @Transactional
    fun update(id: UUID, version: Int, command: StaffCommand): StaffMember {
        val current = load(id, version)
        val currentRole = roles.findById(current.roleId) ?: throw NotFoundException("role", current.roleId)
        val newRole = if (command.roleId == current.roleId) currentRole else assignableRole(command.roleId)
        if (currentRole.template == RoleTemplate.ADMIN && newRole.template != RoleTemplate.ADMIN) ensureAnotherAdmin(current)
        val updated = staff.update(
            current.copy(
                roleId = newRole.id,
                profile = StaffProfile.of(command.fullName, command.position, command.phone),
                granted = if (newRole.system) emptySet() else parsePermissions(command.granted, "granted"),
                denied = if (newRole.system) emptySet() else parsePermissions(command.denied, "denied"),
                updatedAt = clock.instant(),
            ),
        )
        audit.record(AuditEvent("staff.updated", ENTITY, id, before = StaffSnapshot(current), after = StaffSnapshot(updated)))
        val rightsChanged = current.roleId != updated.roleId || current.granted != updated.granted || current.denied != updated.denied
        if (rightsChanged) sessions.terminateAll(id)
        return updated
    }

    @Transactional
    fun deactivate(id: UUID, version: Int): StaffMember {
        val current = load(id, version)
        if (id == CurrentActor.staffId()) throw DomainException("staff.cannot_deactivate_self")
        if (!current.active) return current
        if (roles.findById(current.roleId)?.template == RoleTemplate.ADMIN) ensureAnotherAdmin(current)
        val updated = staff.update(current.copy(active = false, updatedAt = clock.instant()))
        audit.record(AuditEvent("staff.deactivated", ENTITY, id, before = StaffSnapshot(current), after = StaffSnapshot(updated)))
        sessions.terminateAll(id)
        return updated
    }

    @Transactional
    fun activate(id: UUID, version: Int): StaffMember {
        val current = load(id, version)
        if (current.active) return current
        if (roles.findById(current.roleId)?.template != RoleTemplate.VENDOR_SUPPORT) requireLicenseSeat()
        val updated = staff.update(current.copy(active = true, updatedAt = clock.instant()))
        audit.record(AuditEvent("staff.activated", ENTITY, id, before = StaffSnapshot(current), after = StaffSnapshot(updated)))
        return updated
    }

    @Transactional
    fun resetPassword(id: UUID, version: Int): StaffWithTemporaryPassword {
        val current = load(id, version)
        if (id == CurrentActor.staffId()) throw DomainException("staff.use_change_password")
        val password = TemporaryPasswords.generate()
        val now = clock.instant()
        val updated = staff.update(
            current.copy(passwordHash = passwordEncoder.encode(password)!!, mustChangePassword = true, passwordChangedAt = now, updatedAt = now),
        )
        audit.record(AuditEvent("staff.password_reset", ENTITY, id))
        sessions.terminateAll(id)
        return StaffWithTemporaryPassword(updated, password)
    }

    @Transactional(readOnly = true)
    fun signature(id: UUID): Pair<ByteArray, String> = staff.signature(id) ?: throw NotFoundException("signature", id)

    @Transactional
    fun setSignature(id: UUID, bytes: ByteArray) {
        get(id)
        val type = SignatureImage.contentType(bytes)
        staff.setSignature(id, bytes, type, clock.instant())
        audit.record(AuditEvent("staff.signature_changed", ENTITY, id, after = mapOf("contentType" to type, "bytes" to bytes.size)))
    }

    @Transactional
    fun removeSignature(id: UUID) {
        get(id)
        staff.setSignature(id, null, null, clock.instant())
        audit.record(AuditEvent("staff.signature_removed", ENTITY, id))
    }

    private fun assignableRole(roleId: UUID): Role {
        val role = roles.findById(roleId) ?: throw ValidationException("roleId", "validation.unknown_role")
        if (!role.assignable) throw ValidationException("roleId", "validation.role_not_assignable")
        return role
    }

    /** The license limits active staff accounts (the vendor's support account is not counted). */
    private fun requireLicenseSeat() {
        val max = license.maxUsers() ?: return
        if (staff.countActiveLicensed() >= max) throw DomainException("license.max_users_reached", params = mapOf("maxUsers" to max))
    }

    private fun ensureAnotherAdmin(admin: StaffMember) {
        staff.lockAdmins()
        val remaining = staff.countActiveWithTemplate(RoleTemplate.ADMIN) - if (admin.active) 1 else 0
        if (remaining < 1) throw DomainException("staff.last_admin")
    }

    private fun load(id: UUID, version: Int): StaffMember {
        val member = get(id)
        if (member.version != version) throw VersionConflictException(ENTITY, id)
        return member
    }

    private companion object {
        const val ENTITY = "staff"
    }
}
