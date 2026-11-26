package uz.medclinic.clinic.identity.domain

import uz.medclinic.clinic.shared.Permission
import uz.medclinic.platform.domain.Page
import uz.medclinic.platform.domain.PageQuery
import java.time.Instant
import java.util.UUID

data class StaffFilter(val query: String? = null, val roleId: UUID? = null, val includeInactive: Boolean = false)

interface RoleRepository {
    fun insert(role: Role)
    fun findById(id: UUID): Role?
    fun findByTemplate(template: RoleTemplate): Role?
    fun findAll(): List<Role>
    fun update(role: Role): Role
    fun countStaff(roleId: UUID): Long
}

interface StaffRepository {
    fun insert(staff: StaffMember)
    fun findById(id: UUID): StaffMember?
    fun findByLogin(login: String): StaffMember?
    fun search(filter: StaffFilter, page: PageQuery): Page<StaffMember>
    fun update(staff: StaffMember): StaffMember
    fun recordLogin(id: UUID, at: Instant)

    /** Active staff that count against the license user limit (the vendor's support account does not). */
    fun countActiveLicensed(): Long
    fun countActiveWithTemplate(template: RoleTemplate): Long
    fun idsWithRole(roleId: UUID): List<UUID>
    fun activeWith(permission: Permission): List<Pair<UUID, String>>
    fun activeAll(): List<Pair<UUID, String>>

    /** Serialises concurrent changes to Admin accounts (last-Admin rule) inside the current transaction. */
    fun lockAdmins()

    fun signature(id: UUID): Pair<ByteArray, String>?
    fun setSignature(id: UUID, bytes: ByteArray?, contentType: String?, at: Instant)
}
