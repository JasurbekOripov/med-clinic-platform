package uz.medclinic.clinic.identity.web

import jakarta.validation.Valid
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uz.medclinic.clinic.identity.application.RoleService
import uz.medclinic.clinic.identity.application.RoleWithUsage
import uz.medclinic.clinic.shared.Permission
import uz.medclinic.clinic.shared.PermissionGroup
import uz.medclinic.clinic.shared.Permissions
import uz.medclinic.platform.domain.ValidationException
import java.util.UUID

data class RoleResponse(
    val id: UUID,
    /** Template code for translated display (null for custom roles). */
    val template: String?,
    val name: String?,
    val system: Boolean,
    val assignable: Boolean,
    val permissions: List<String>,
    val activeStaff: Long,
    val version: Int,
) {
    constructor(r: RoleWithUsage) : this(
        r.role.id, r.role.template?.name, r.role.name, r.role.system, r.role.assignable,
        r.role.effectivePermissions.map { it.name }.sorted(), r.activeStaff, r.role.version,
    )
}

data class RoleRequest(
    @field:Size(max = 100) val name: String?,
    @field:Size(max = 100) val permissions: List<String> = emptyList(),
    @field:PositiveOrZero val version: Int? = null,
)

data class PermissionGroupResponse(val group: PermissionGroup, val permissions: List<String>)

@RestController
@RequestMapping("/api/v1")
class RoleController(private val roles: RoleService) {
    @GetMapping("/roles")
    @PreAuthorize(Permissions.HAS_STAFF_VIEW)
    fun list() = roles.list().map(::RoleResponse)

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.HAS_ROLE_MANAGE)
    fun create(@Valid @RequestBody body: RoleRequest) = RoleResponse(RoleWithUsage(roles.create(body.name, body.permissions), 0))

    @PutMapping("/roles/{id}")
    @PreAuthorize(Permissions.HAS_ROLE_MANAGE)
    fun update(@PathVariable id: UUID, @Valid @RequestBody body: RoleRequest): RoleResponse {
        val version = body.version ?: throw ValidationException("version", "validation.not_null")
        val role = roles.update(id, version, body.name, body.permissions)
        return RoleResponse(roles.list().first { it.role.id == role.id })
    }

    @DeleteMapping("/roles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Permissions.HAS_ROLE_MANAGE)
    fun delete(@PathVariable id: UUID, @RequestParam version: Int) = roles.delete(id, version)

    /** The permission catalogue grouped for the role editor. */
    @GetMapping("/permissions")
    @PreAuthorize(Permissions.HAS_STAFF_VIEW)
    fun permissions() = PermissionGroup.entries.map { g ->
        PermissionGroupResponse(g, Permission.entries.filter { it.group == g }.map { it.name })
    }
}
