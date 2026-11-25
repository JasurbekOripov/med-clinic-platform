package uz.medclinic.clinic.identity.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.clinic.audit.api.AuditEvent
import uz.medclinic.clinic.audit.api.AuditRecorder
import uz.medclinic.clinic.identity.domain.Role
import uz.medclinic.clinic.identity.domain.RoleRepository
import uz.medclinic.clinic.identity.domain.StaffRepository
import uz.medclinic.clinic.identity.domain.parsePermissions
import uz.medclinic.clinic.identity.infrastructure.SessionTerminator
import uz.medclinic.common.Ids
import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.domain.NotFoundException
import uz.medclinic.platform.domain.VersionConflictException
import java.time.Clock
import java.util.UUID

data class RoleWithUsage(val role: Role, val activeStaff: Long)

@Service
class RoleService(
    private val roles: RoleRepository,
    private val staff: StaffRepository,
    private val audit: AuditRecorder,
    private val sessions: SessionTerminator,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun list(): List<RoleWithUsage> = roles.findAll().map { RoleWithUsage(it, roles.countStaff(it.id)) }

    @Transactional
    fun create(name: String?, permissionCodes: List<String>): Role {
        val role = Role.custom(Ids.newId(), name, parsePermissions(permissionCodes, "permissions"), clock.instant())
        roles.insert(role)
        audit.record(AuditEvent("role.created", ENTITY, role.id, after = snapshot(role)))
        return role
    }

    /** Changing permissions ends the sessions of everyone with this role, so nobody keeps stale rights. */
    @Transactional
    fun update(id: UUID, version: Int, name: String?, permissionCodes: List<String>): Role {
        val current = load(id, version)
        val updated = roles.update(current.edited(name, parsePermissions(permissionCodes, "permissions"), clock.instant()))
        audit.record(AuditEvent("role.updated", ENTITY, id, before = snapshot(current), after = snapshot(updated)))
        if (current.permissions != updated.permissions) staff.idsWithRole(id).forEach { sessions.terminateAll(it) }
        return updated
    }

    @Transactional
    fun delete(id: UUID, version: Int) {
        val current = load(id, version)
        if (current.system || current.template != null) throw DomainException("role.template_locked")
        if (roles.countStaff(id) > 0 || staff.idsWithRole(id).isNotEmpty()) throw DomainException("role.in_use")
        roles.update(current.copy(deletedAt = clock.instant()))
        audit.record(AuditEvent("role.deleted", ENTITY, id, before = snapshot(current)))
    }

    private fun load(id: UUID, version: Int): Role {
        val role = roles.findById(id) ?: throw NotFoundException(ENTITY, id)
        if (role.version != version) throw VersionConflictException(ENTITY, id)
        return role
    }

    private fun snapshot(r: Role) = mapOf("template" to r.template, "name" to r.name, "permissions" to r.permissions.map { it.name }.sorted())

    private companion object {
        const val ENTITY = "role"
    }
}
