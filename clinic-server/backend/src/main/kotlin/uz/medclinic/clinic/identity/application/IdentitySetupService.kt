package uz.medclinic.clinic.identity.application

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.clinic.audit.api.AuditEvent
import uz.medclinic.clinic.audit.api.AuditRecorder
import uz.medclinic.clinic.identity.api.CreatedStaff
import uz.medclinic.clinic.identity.api.IdentitySetup
import uz.medclinic.clinic.identity.api.NewStaff
import uz.medclinic.clinic.identity.api.StaffDirectory
import uz.medclinic.clinic.identity.api.StaffRef
import uz.medclinic.clinic.identity.domain.PasswordPolicy
import uz.medclinic.clinic.identity.domain.Role
import uz.medclinic.clinic.identity.domain.RoleRepository
import uz.medclinic.clinic.identity.domain.RoleTemplate
import uz.medclinic.clinic.identity.domain.StaffFilter
import uz.medclinic.clinic.identity.domain.StaffLogin
import uz.medclinic.clinic.identity.domain.StaffRepository
import uz.medclinic.clinic.shared.Permission
import uz.medclinic.common.Ids
import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.domain.PageQuery
import java.time.Clock
import java.util.UUID

@Service
class IdentitySetupService(
    private val roles: RoleRepository,
    private val staff: StaffRepository,
    private val staffService: StaffService,
    private val passwordEncoder: PasswordEncoder,
    private val audit: AuditRecorder,
    private val clock: Clock,
) : IdentitySetup, StaffDirectory {
    @Transactional
    override fun seedRoles() {
        val now = clock.instant()
        RoleTemplate.entries.filter { roles.findByTemplate(it) == null }.forEach { template ->
            val role = Role.fromTemplate(Ids.newId(), template, now)
            roles.insert(role)
            audit.record(AuditEvent("role.created", "role", role.id, after = mapOf("template" to template)))
        }
    }

    @Transactional(readOnly = true)
    override fun activeWith(permission: Permission): List<StaffRef> = staff.activeWith(permission).map { (id, name) -> StaffRef(id, name) }

    @Transactional(readOnly = true)
    override fun hasActiveAdmin(): Boolean = staff.countActiveWithTemplate(RoleTemplate.ADMIN) > 0

    @Transactional
    override fun createFirstAdmin(fullName: String, login: String, password: String, vendorSupportPassword: String?): UUID {
        staff.lockAdmins()
        if (hasActiveAdmin()) throw DomainException("setup.already_completed", DomainException.Kind.CONFLICT)
        seedRoles()
        PasswordPolicy.validate(password)
        val admin = staffService.insert(
            StaffLogin.validate(login), StaffCommand(fullName, null, null, roleOf(RoleTemplate.ADMIN).id),
            roleOf(RoleTemplate.ADMIN), passwordEncoder.encode(password)!!, mustChange = false,
        )
        if (!vendorSupportPassword.isNullOrBlank()) {
            PasswordPolicy.validate(vendorSupportPassword, field = "vendorSupportPassword")
            val role = roleOf(RoleTemplate.VENDOR_SUPPORT)
            staffService.insert(
                VENDOR_SUPPORT_LOGIN, StaffCommand("Vendor support", null, null, role.id), role,
                passwordEncoder.encode(vendorSupportPassword)!!, mustChange = false,
            )
        }
        return admin.id
    }

    override fun normaliseLogin(raw: String): String? = runCatching { StaffLogin.validate(raw) }.getOrNull()

    @Transactional(readOnly = true)
    override fun fullNameExists(fullName: String): Boolean =
        staff.search(StaffFilter(query = fullName, includeInactive = true), PageQuery(0, 50)).items
            .any { it.profile.fullName.equals(fullName.trim(), ignoreCase = true) }

    @Transactional(readOnly = true)
    override fun loginExists(login: String): Boolean = staff.findByLogin(StaffLogin.normalise(login)) != null

    @Transactional(readOnly = true)
    override fun findRoleId(codeOrName: String): UUID? {
        val key = codeOrName.trim()
        return roles.findAll()
            .filter { it.assignable }
            .firstOrNull { it.template?.name.equals(key, ignoreCase = true) || it.name.equals(key, ignoreCase = true) }
            ?.id
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun createAll(staff: List<NewStaff>): List<CreatedStaff> = staff.map { s ->
        val result = staffService.create(s.login, StaffCommand(s.fullName, s.position, s.phone, s.roleId))
        CreatedStaff(result.staff.id, result.staff.login, result.staff.profile.fullName, result.temporaryPassword)
    }

    private fun roleOf(template: RoleTemplate): Role = roles.findByTemplate(template) ?: error("role template $template missing")

    companion object {
        const val VENDOR_SUPPORT_LOGIN = "vendor.support"
    }
}
