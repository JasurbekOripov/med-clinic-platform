package uz.medclinic.clinic.identity.application

import org.springframework.stereotype.Component
import uz.medclinic.clinic.identity.domain.RoleRepository
import uz.medclinic.clinic.identity.domain.StaffMember
import uz.medclinic.clinic.identity.security.StaffPrincipal
import uz.medclinic.platform.domain.NotFoundException
import java.time.Instant

@Component
class Principals(private val roles: RoleRepository) {
    fun of(staff: StaffMember, authenticatedAt: Instant): StaffPrincipal {
        val role = roles.findById(staff.roleId) ?: throw NotFoundException("role", staff.roleId)
        return StaffPrincipal(
            id = staff.id,
            login = staff.login,
            fullName = staff.profile.fullName,
            roleId = role.id,
            roleTemplate = role.template?.name,
            roleName = role.name,
            permissions = staff.effectivePermissions(role).map { it.name }.toSet(),
            mustChangePassword = staff.mustChangePassword,
            authenticatedAt = authenticatedAt,
        )
    }
}
