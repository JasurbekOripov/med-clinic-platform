package uz.medclinic.vendor.tenancy.infrastructure

import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import uz.medclinic.common.UzPhone
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
import uz.medclinic.vendor.tenancy.api.TenantStatus
import uz.medclinic.vendor.tenancy.domain.Branch
import uz.medclinic.vendor.tenancy.domain.BranchDetails
import uz.medclinic.vendor.tenancy.domain.BranchRepository
import uz.medclinic.vendor.tenancy.domain.Clinic
import uz.medclinic.vendor.tenancy.domain.ClinicDetails
import uz.medclinic.vendor.tenancy.domain.ClinicFilter
import uz.medclinic.vendor.tenancy.domain.ClinicListItem
import uz.medclinic.vendor.tenancy.domain.ClinicRepository
import uz.medclinic.vendor.tenancy.domain.Region
import java.sql.ResultSet
import java.util.UUID

@Repository
class JdbcClinicRepository(private val jdbc: JdbcClient) : ClinicRepository {
    override fun insert(clinic: Clinic): Clinic {
        val code = jdbc.sql(
            """
            insert into clinic (id, code, name, legal_name, owner_name, phone, address, region, notes, status,
                                created_at, updated_at, version)
            values (:id, 'CL-' || lpad(nextval('clinic_code_seq')::text, 4, '0'), :name, :legalName, :ownerName, :phone,
                    :address, :region, :notes, :status, :createdAt, :updatedAt, 0)
            returning code
            """,
        ).params(params(clinic)).query(String::class.java).single()
        return clinic.copy(code = code)
    }

    override fun findById(id: UUID): Clinic? =
        jdbc.sql("select * from clinic where id = :id and deleted_at is null").param("id", id).query(::mapClinic).optional().orElse(null)

    override fun findByIds(ids: Collection<UUID>): List<Clinic> {
        if (ids.isEmpty()) return emptyList()
        return jdbc.sql("select * from clinic where id in (:ids) and deleted_at is null").param("ids", ids).query(::mapClinic).list()
    }

    override fun search(filter: ClinicFilter, page: PageQuery): Page<ClinicListItem> {
        val conditions = mutableListOf("c.deleted_at is null")
        val params = mutableMapOf<String, Any?>()
        filter.status?.let { conditions += "c.status = :status"; params["status"] = it.name }
        filter.region?.let { conditions += "c.region = :region"; params["region"] = it.name }
        filter.query?.let { q ->
            params["q"] = likePattern(q)
            val digits = q.filter(Char::isDigit)
            val phoneCondition = if (digits.length >= 3) {
                params["digits"] = likePattern(digits); " or c.phone like :digits escape '\\'"
            } else {
                ""
            }
            conditions += "(c.code ilike :q escape '\\' or c.name ilike :q escape '\\' or c.legal_name ilike :q escape '\\'" +
                " or c.owner_name ilike :q escape '\\'$phoneCondition)"
        }
        val where = "where " + conditions.joinToString(" and ")
        val total = jdbc.sql("select count(*) from clinic c $where").params(params).query(Long::class.java).single()
        val items = jdbc.sql(
            """
            select c.*, (select count(*) from branch b where b.clinic_id = c.id and b.deleted_at is null) as branch_count
            from clinic c $where
            order by c.name, c.id
            limit :limit offset :offset
            """,
        ).params(params).param("limit", page.size).param("offset", page.offset)
            .query { rs, row -> ClinicListItem(mapClinic(rs, row), rs.getInt("branch_count")) }
            .list()
        return Page(items, page.page, page.size, total)
    }

    override fun update(clinic: Clinic): Clinic {
        val rows = jdbc.sql(
            """
            update clinic
            set name = :name, legal_name = :legalName, owner_name = :ownerName, phone = :phone, address = :address,
                region = :region, notes = :notes, status = :status, updated_at = :updatedAt, deleted_at = :deletedAt,
                version = version + 1
            where id = :id and version = :version and deleted_at is null
            """,
        ).params(params(clinic)).param("version", clinic.version).update()
        requireVersionMatched(rows, "clinic", clinic.id)
        return clinic.copy(version = clinic.version + 1)
    }

    override fun nextBranchNumber(clinicId: UUID): Int =
        jdbc.sql("update clinic set next_branch_no = next_branch_no + 1 where id = :id returning next_branch_no - 1")
            .param("id", clinicId).query(Int::class.java).single()

    private fun params(c: Clinic) = mapOf(
        "id" to c.id,
        "name" to c.details.name,
        "legalName" to c.details.legalName,
        "ownerName" to c.details.ownerName,
        "phone" to c.details.phone.e164,
        "address" to c.details.address,
        "region" to c.details.region.name,
        "notes" to c.details.notes,
        "status" to c.status.name,
        "createdAt" to c.createdAt.toDb(),
        "updatedAt" to c.updatedAt.toDb(),
        "deletedAt" to c.deletedAt.toDbOrNull(),
    )
}

internal fun mapClinic(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = Clinic(
    id = rs.uuid("id"),
    code = rs.getString("code"),
    details = ClinicDetails(
        name = rs.getString("name"),
        legalName = rs.getString("legal_name"),
        ownerName = rs.getString("owner_name"),
        phone = UzPhone.of(rs.getString("phone")),
        address = rs.getString("address"),
        region = Region.valueOf(rs.getString("region")),
        notes = rs.getString("notes"),
    ),
    status = TenantStatus.valueOf(rs.getString("status")),
    createdAt = rs.instant("created_at"),
    updatedAt = rs.instant("updated_at"),
    deletedAt = rs.instantOrNull("deleted_at"),
    version = rs.getInt("version"),
)

@Repository
class JdbcBranchRepository(private val jdbc: JdbcClient) : BranchRepository {
    override fun insert(branch: Branch) {
        try {
            jdbc.sql(
                """
                insert into branch (id, clinic_id, code, name, address, phone, status, created_at, updated_at, version)
                values (:id, :clinicId, :code, :name, :address, :phone, :status, :createdAt, :updatedAt, 0)
                """,
            ).params(params(branch)).update()
        } catch (_: DuplicateKeyException) {
            throw nameTaken(branch)
        }
    }

    override fun findById(id: UUID): Branch? =
        jdbc.sql("select * from branch where id = :id and deleted_at is null").param("id", id).query(::map).optional().orElse(null)

    override fun findByClinic(clinicId: UUID, includeArchived: Boolean): List<Branch> =
        jdbc.sql(
            """
            select * from branch
            where clinic_id = :clinicId and deleted_at is null and (:includeArchived or status <> 'ARCHIVED')
            order by code
            """,
        ).param("clinicId", clinicId).param("includeArchived", includeArchived).query(::map).list()

    override fun findByIds(ids: Collection<UUID>): List<Branch> {
        if (ids.isEmpty()) return emptyList()
        return jdbc.sql("select * from branch where id in (:ids) and deleted_at is null").param("ids", ids).query(::map).list()
    }

    override fun findAllLive(): List<Branch> =
        jdbc.sql(
            """
            select b.* from branch b join clinic c on c.id = b.clinic_id
            where b.deleted_at is null and b.status <> 'ARCHIVED' and c.deleted_at is null and c.status <> 'ARCHIVED'
            order by b.code
            """,
        ).query(::map).list()

    override fun update(branch: Branch): Branch {
        val rows = try {
            jdbc.sql(
                """
                update branch
                set name = :name, address = :address, phone = :phone, status = :status, updated_at = :updatedAt,
                    deleted_at = :deletedAt, version = version + 1
                where id = :id and version = :version and deleted_at is null
                """,
            ).params(params(branch)).param("version", branch.version).param("deletedAt", branch.deletedAt.toDbOrNull()).update()
        } catch (_: DuplicateKeyException) {
            throw nameTaken(branch)
        }
        requireVersionMatched(rows, "branch", branch.id)
        return branch.copy(version = branch.version + 1)
    }

    override fun countLive(clinicId: UUID): Int =
        jdbc.sql("select count(*) from branch where clinic_id = :clinicId and deleted_at is null and status <> 'ARCHIVED'")
            .param("clinicId", clinicId).query(Int::class.java).single()

    private fun nameTaken(branch: Branch) =
        DomainException("branch.name_taken", DomainException.Kind.CONFLICT, mapOf("name" to branch.details.name))

    private fun params(b: Branch) = mapOf(
        "id" to b.id,
        "clinicId" to b.clinicId,
        "code" to b.code,
        "name" to b.details.name,
        "address" to b.details.address,
        "phone" to b.details.phone?.e164,
        "status" to b.status.name,
        "createdAt" to b.createdAt.toDb(),
        "updatedAt" to b.updatedAt.toDb(),
    )

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = Branch(
        id = rs.uuid("id"),
        clinicId = rs.uuid("clinic_id"),
        code = rs.getString("code"),
        details = BranchDetails(rs.getString("name"), rs.getString("address"), rs.getString("phone")?.let(UzPhone::of)),
        status = TenantStatus.valueOf(rs.getString("status")),
        createdAt = rs.instant("created_at"),
        updatedAt = rs.instant("updated_at"),
        deletedAt = rs.instantOrNull("deleted_at"),
        version = rs.getInt("version"),
    )
}
