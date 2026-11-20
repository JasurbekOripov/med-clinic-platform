package uz.medclinic.vendor.identity.infrastructure

import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import uz.medclinic.vendor.identity.domain.StaffAccount
import uz.medclinic.vendor.identity.domain.StaffRepository
import uz.medclinic.vendor.identity.domain.VendorRole
import uz.medclinic.vendor.shared.domain.DomainException
import uz.medclinic.vendor.shared.domain.Page
import uz.medclinic.vendor.shared.domain.PageQuery
import uz.medclinic.vendor.shared.persistence.instant
import uz.medclinic.vendor.shared.persistence.instantOrNull
import uz.medclinic.vendor.shared.persistence.likePattern
import uz.medclinic.vendor.shared.persistence.requireVersionMatched
import uz.medclinic.vendor.shared.persistence.toDb
import uz.medclinic.vendor.shared.persistence.toDbOrNull
import uz.medclinic.vendor.shared.persistence.uuid
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@Repository
class JdbcStaffRepository(private val jdbc: JdbcClient) : StaffRepository {
    override fun findById(id: UUID): StaffAccount? =
        jdbc.sql("select * from vendor_staff where id = :id").param("id", id).query(::map).optional().orElse(null)

    override fun findByLogin(login: String): StaffAccount? =
        jdbc.sql("select * from vendor_staff where lower(login) = lower(:login)").param("login", login)
            .query(::map).optional().orElse(null)

    override fun search(query: String?, includeInactive: Boolean, page: PageQuery): Page<StaffAccount> {
        val where = buildString {
            append("where (:includeInactive or active)")
            if (!query.isNullOrBlank()) append(" and (login ilike :q escape '\\' or full_name ilike :q escape '\\')")
        }
        val params = mapOf("includeInactive" to includeInactive, "q" to query?.let(::likePattern))
        val total = jdbc.sql("select count(*) from vendor_staff $where").params(params).query(Long::class.java).single()
        val items = jdbc.sql("select * from vendor_staff $where order by active desc, full_name, id limit :limit offset :offset")
            .params(params).param("limit", page.size).param("offset", page.offset).query(::map).list()
        return Page(items, page.page, page.size, total)
    }

    override fun count(): Long = jdbc.sql("select count(*) from vendor_staff").query(Long::class.java).single()

    override fun countActiveOwners(): Long =
        jdbc.sql("select count(*) from vendor_staff where active and role = 'OWNER'").query(Long::class.java).single()

    override fun insert(staff: StaffAccount) {
        try {
            jdbc.sql(
                """
                insert into vendor_staff (id, login, full_name, role, password_hash, active, must_change_password,
                                          password_changed_at, last_login_at, created_at, updated_at, version)
                values (:id, :login, :fullName, :role, :passwordHash, :active, :mustChangePassword,
                        :passwordChangedAt, :lastLoginAt, :createdAt, :updatedAt, 0)
                """,
            ).params(params(staff)).update()
        } catch (_: DuplicateKeyException) {
            throw DomainException("staff.login_taken", DomainException.Kind.CONFLICT, mapOf("login" to staff.login))
        }
    }

    override fun update(staff: StaffAccount): StaffAccount {
        val rows = jdbc.sql(
            """
            update vendor_staff
            set full_name = :fullName, role = :role, password_hash = :passwordHash, active = :active,
                must_change_password = :mustChangePassword, password_changed_at = :passwordChangedAt,
                updated_at = :updatedAt, version = version + 1
            where id = :id and version = :version
            """,
        ).params(params(staff)).param("version", staff.version).update()
        requireVersionMatched(rows, "staff", staff.id)
        return staff.copy(version = staff.version + 1)
    }

    override fun recordLogin(id: UUID, at: Instant) {
        // Not a business edit: does not bump the version, so it never conflicts with an admin's form.
        jdbc.sql("update vendor_staff set last_login_at = :at where id = :id").param("id", id).param("at", at.toDb()).update()
    }

    override fun lockOwners() {
        jdbc.sql("select id from vendor_staff where role = 'OWNER' order by id for update").query(UUID::class.java).list()
    }

    private fun params(staff: StaffAccount) = mapOf(
        "id" to staff.id,
        "login" to staff.login,
        "fullName" to staff.fullName,
        "role" to staff.role.name,
        "passwordHash" to staff.passwordHash,
        "active" to staff.active,
        "mustChangePassword" to staff.mustChangePassword,
        "passwordChangedAt" to staff.passwordChangedAt.toDb(),
        "lastLoginAt" to staff.lastLoginAt.toDbOrNull(),
        "createdAt" to staff.createdAt.toDb(),
        "updatedAt" to staff.updatedAt.toDb(),
    )

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = StaffAccount(
        id = rs.uuid("id"),
        login = rs.getString("login"),
        fullName = rs.getString("full_name"),
        role = VendorRole.valueOf(rs.getString("role")),
        passwordHash = rs.getString("password_hash"),
        active = rs.getBoolean("active"),
        mustChangePassword = rs.getBoolean("must_change_password"),
        passwordChangedAt = rs.instant("password_changed_at"),
        lastLoginAt = rs.instantOrNull("last_login_at"),
        createdAt = rs.instant("created_at"),
        updatedAt = rs.instant("updated_at"),
        version = rs.getInt("version"),
    )
}
