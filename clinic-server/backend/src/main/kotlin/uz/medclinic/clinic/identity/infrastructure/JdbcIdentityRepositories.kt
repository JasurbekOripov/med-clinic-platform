package uz.medclinic.clinic.identity.infrastructure

import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import uz.medclinic.clinic.identity.domain.Role
import uz.medclinic.clinic.identity.domain.RoleRepository
import uz.medclinic.clinic.identity.domain.RoleTemplate
import uz.medclinic.clinic.identity.domain.StaffFilter
import uz.medclinic.clinic.identity.domain.StaffMember
import uz.medclinic.clinic.identity.domain.StaffProfile
import uz.medclinic.clinic.identity.domain.StaffRepository
import uz.medclinic.clinic.shared.ClinicContext
import uz.medclinic.clinic.shared.Permission
import uz.medclinic.common.UzPhone
import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.domain.Page
import uz.medclinic.platform.domain.PageQuery
import uz.medclinic.platform.persistence.instant
import uz.medclinic.platform.persistence.instantOrNull
import uz.medclinic.platform.persistence.likePattern
import uz.medclinic.platform.persistence.requireVersionMatched
import uz.medclinic.platform.persistence.toDb
import uz.medclinic.platform.persistence.toDbOrNull
import uz.medclinic.platform.persistence.uuid
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@Suppress("UNCHECKED_CAST")
private fun ResultSet.permissions(column: String): Set<Permission> =
    (getArray(column).array as Array<String>)
        // Codes removed from the catalogue in a future version are ignored instead of failing.
        .mapNotNull { code -> Permission.entries.firstOrNull { it.name == code } }
        .toSet()

private fun Set<Permission>.toDb(): Array<String> = map { it.name }.sorted().toTypedArray()

@Repository
class JdbcRoleRepository(private val jdbc: JdbcClient, private val clinic: ClinicContext) : RoleRepository {
    override fun insert(role: Role) {
        val identity = clinic.current()
        try {
            jdbc.sql(
                """
                insert into role (id, clinic_id, branch_id, template, name, system, permissions, created_at, updated_at, version)
                values (:id, :clinicId, :branchId, :template, :name, :system, :permissions, :createdAt, :updatedAt, 0)
                """,
            )
                .param("id", role.id).param("clinicId", identity.clinicId).param("branchId", identity.branchId)
                .param("template", role.template?.name).param("name", role.name).param("system", role.system)
                .param("permissions", role.permissions.toDb())
                .param("createdAt", role.createdAt.toDb()).param("updatedAt", role.updatedAt.toDb())
                .update()
        } catch (_: DuplicateKeyException) {
            throw DomainException("role.name_taken", DomainException.Kind.CONFLICT)
        }
    }

    override fun findById(id: UUID): Role? =
        jdbc.sql("select * from role where id = :id and deleted_at is null").param("id", id).query(::map).optional().orElse(null)

    override fun findByTemplate(template: RoleTemplate): Role? =
        jdbc.sql("select * from role where template = :t and deleted_at is null").param("t", template.name).query(::map).optional().orElse(null)

    override fun findAll(): List<Role> =
        jdbc.sql("select * from role where deleted_at is null order by template is null, created_at, id").query(::map).list()

    override fun update(role: Role): Role {
        val rows = try {
            jdbc.sql(
                """
                update role set name = :name, permissions = :permissions, updated_at = :updatedAt, deleted_at = :deletedAt,
                                version = version + 1
                where id = :id and version = :version and deleted_at is null
                """,
            )
                .param("id", role.id).param("version", role.version).param("name", role.name)
                .param("permissions", role.permissions.toDb()).param("updatedAt", role.updatedAt.toDb())
                .param("deletedAt", role.deletedAt.toDbOrNull())
                .update()
        } catch (_: DuplicateKeyException) {
            throw DomainException("role.name_taken", DomainException.Kind.CONFLICT)
        }
        requireVersionMatched(rows, "role", role.id)
        return role.copy(version = role.version + 1)
    }

    override fun countStaff(roleId: UUID): Long =
        jdbc.sql("select count(*) from staff where role_id = :id and active").param("id", roleId).query(Long::class.java).single()

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = Role(
        id = rs.uuid("id"),
        template = rs.getString("template")?.let(RoleTemplate::valueOf),
        name = rs.getString("name"),
        system = rs.getBoolean("system"),
        permissions = rs.permissions("permissions"),
        createdAt = rs.instant("created_at"),
        updatedAt = rs.instant("updated_at"),
        deletedAt = rs.instantOrNull("deleted_at"),
        version = rs.getInt("version"),
    )
}

@Repository
class JdbcStaffRepository(private val jdbc: JdbcClient, private val clinic: ClinicContext) : StaffRepository {
    override fun insert(staff: StaffMember) {
        val identity = clinic.current()
        try {
            jdbc.sql(
                """
                insert into staff (id, clinic_id, branch_id, role_id, login, full_name, position, phone, password_hash, active,
                                   must_change_password, password_changed_at, granted, denied, created_at, updated_at, version)
                values (:id, :clinicId, :branchId, :roleId, :login, :fullName, :position, :phone, :passwordHash, :active,
                        :mustChangePassword, :passwordChangedAt, :granted, :denied, :createdAt, :updatedAt, 0)
                """,
            ).params(params(staff)).param("clinicId", identity.clinicId).param("branchId", identity.branchId).update()
        } catch (_: DuplicateKeyException) {
            throw DomainException("staff.login_taken", DomainException.Kind.CONFLICT, mapOf("login" to staff.login))
        }
    }

    override fun findById(id: UUID): StaffMember? = one("select $COLUMNS from staff where id = :v", id)

    override fun findByLogin(login: String): StaffMember? = one("select $COLUMNS from staff where lower(login) = lower(:v)", login)

    override fun search(filter: StaffFilter, page: PageQuery): Page<StaffMember> {
        val conditions = mutableListOf("(:includeInactive or active)")
        val params = mutableMapOf<String, Any?>("includeInactive" to filter.includeInactive)
        filter.roleId?.let { conditions += "role_id = :roleId"; params["roleId"] = it }
        filter.query?.let { q ->
            params["q"] = likePattern(q)
            val digits = q.filter(Char::isDigit)
            // Phone search only when the user typed enough digits to be meaningful.
            val phone = if (digits.length >= 3) {
                params["digits"] = likePattern(digits); " or coalesce(phone, '') like :digits escape '\\'"
            } else {
                ""
            }
            conditions += "(full_name ilike :q escape '\\' or login ilike :q escape '\\'$phone)"
        }
        val where = "where " + conditions.joinToString(" and ")
        val total = jdbc.sql("select count(*) from staff $where").params(params).query(Long::class.java).single()
        val items = jdbc.sql("select $COLUMNS from staff $where order by active desc, full_name, id limit :limit offset :offset")
            .params(params).param("limit", page.size).param("offset", page.offset).query(::map).list()
        return Page(items, page.page, page.size, total)
    }

    override fun update(staff: StaffMember): StaffMember {
        val rows = jdbc.sql(
            """
            update staff
            set role_id = :roleId, full_name = :fullName, position = :position, phone = :phone, password_hash = :passwordHash,
                active = :active, must_change_password = :mustChangePassword, password_changed_at = :passwordChangedAt,
                granted = :granted, denied = :denied, updated_at = :updatedAt, version = version + 1
            where id = :id and version = :version
            """,
        ).params(params(staff)).param("version", staff.version).update()
        requireVersionMatched(rows, "staff", staff.id)
        return staff.copy(version = staff.version + 1)
    }

    override fun recordLogin(id: UUID, at: Instant) {
        jdbc.sql("update staff set last_login_at = :at where id = :id").param("id", id).param("at", at.toDb()).update()
    }

    override fun countActiveLicensed(): Long =
        jdbc.sql(
            """
            select count(*) from staff s join role r on r.id = s.role_id
            where s.active and coalesce(r.template, '') <> 'VENDOR_SUPPORT'
            """,
        ).query(Long::class.java).single()

    override fun countActiveWithTemplate(template: RoleTemplate): Long =
        jdbc.sql("select count(*) from staff s join role r on r.id = s.role_id where s.active and r.template = :t")
            .param("t", template.name).query(Long::class.java).single()

    override fun idsWithRole(roleId: UUID): List<UUID> =
        jdbc.sql("select id from staff where role_id = :id").param("id", roleId).query { rs, _ -> rs.uuid("id") }.list()

    override fun activeWith(permission: Permission): List<Pair<UUID, String>> =
        jdbc.sql(
            """
            select s.id, s.full_name from staff s join role r on r.id = s.role_id
            where s.active and not r.system and r.deleted_at is null
              and (:p = any(r.permissions) or :p = any(s.granted)) and not (:p = any(s.denied))
            order by s.full_name, s.id
            """,
        ).param("p", permission.name).query { rs, _ -> rs.uuid("id") to rs.getString("full_name") }.list()

    override fun activeAll(): List<Pair<UUID, String>> =
        jdbc.sql(
            "select s.id, s.full_name from staff s join role r on r.id = s.role_id where s.active and r.template is distinct from 'VENDOR_SUPPORT' order by s.full_name, s.id",
        ).query { rs, _ -> rs.uuid("id") to rs.getString("full_name") }.list()

    override fun lockAdmins() {
        jdbc.sql(
            "select s.id from staff s join role r on r.id = s.role_id where r.template = 'ADMIN' order by s.id for update of s",
        ).query { rs, _ -> rs.uuid("id") }.list()
    }

    override fun signature(id: UUID): Pair<ByteArray, String>? =
        jdbc.sql("select signature, signature_type from staff where id = :id and signature is not null").param("id", id)
            .query { rs, _ -> rs.getBytes("signature") to rs.getString("signature_type") }.optional().orElse(null)

    override fun setSignature(id: UUID, bytes: ByteArray?, contentType: String?, at: Instant) {
        jdbc.sql("update staff set signature = :bytes, signature_type = :type, updated_at = :at where id = :id")
            .param("id", id).param("bytes", bytes).param("type", contentType).param("at", at.toDb()).update()
    }

    private fun one(sql: String, value: Any): StaffMember? = jdbc.sql(sql).param("v", value).query(::map).optional().orElse(null)

    private fun params(s: StaffMember) = mapOf(
        "id" to s.id,
        "roleId" to s.roleId,
        "login" to s.login,
        "fullName" to s.profile.fullName,
        "position" to s.profile.position,
        "phone" to s.profile.phone?.e164,
        "passwordHash" to s.passwordHash,
        "active" to s.active,
        "mustChangePassword" to s.mustChangePassword,
        "passwordChangedAt" to s.passwordChangedAt.toDb(),
        "granted" to s.granted.toDb(),
        "denied" to s.denied.toDb(),
        "createdAt" to s.createdAt.toDb(),
        "updatedAt" to s.updatedAt.toDb(),
    )

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = StaffMember(
        id = rs.uuid("id"),
        roleId = rs.uuid("role_id"),
        login = rs.getString("login"),
        profile = StaffProfile(rs.getString("full_name"), rs.getString("position"), rs.getString("phone")?.let(UzPhone::of)),
        passwordHash = rs.getString("password_hash"),
        active = rs.getBoolean("active"),
        mustChangePassword = rs.getBoolean("must_change_password"),
        passwordChangedAt = rs.instant("password_changed_at"),
        lastLoginAt = rs.instantOrNull("last_login_at"),
        granted = rs.permissions("granted"),
        denied = rs.permissions("denied"),
        hasSignature = rs.getBoolean("has_signature"),
        createdAt = rs.instant("created_at"),
        updatedAt = rs.instant("updated_at"),
        version = rs.getInt("version"),
    )

    private companion object {
        /** Never selects the signature bytes in lists. */
        const val COLUMNS = "id, role_id, login, full_name, position, phone, password_hash, active, must_change_password, " +
            "password_changed_at, last_login_at, granted, denied, signature is not null as has_signature, created_at, updated_at, version"
    }
}
