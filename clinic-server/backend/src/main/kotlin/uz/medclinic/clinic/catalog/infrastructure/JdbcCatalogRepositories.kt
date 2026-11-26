package uz.medclinic.clinic.catalog.infrastructure

import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import uz.medclinic.clinic.catalog.domain.CatalogConflict
import uz.medclinic.clinic.catalog.domain.DiscountType
import uz.medclinic.clinic.catalog.domain.DiscountTypeRepository
import uz.medclinic.clinic.catalog.domain.ServiceDetails
import uz.medclinic.clinic.catalog.domain.ServiceGroup
import uz.medclinic.clinic.catalog.domain.ServiceGroupRepository
import uz.medclinic.clinic.catalog.domain.ServiceItem
import uz.medclinic.clinic.catalog.domain.ServiceItemRepository
import uz.medclinic.clinic.shared.ClinicContext
import uz.medclinic.platform.persistence.instant
import uz.medclinic.platform.persistence.instantOrNull
import uz.medclinic.platform.persistence.requireVersionMatched
import uz.medclinic.platform.persistence.toDb
import uz.medclinic.platform.persistence.toDbOrNull
import uz.medclinic.platform.persistence.uuid
import java.sql.ResultSet
import java.util.UUID

/** Unique indexes are the final guard against two Admins saving the same name at once. */
private inline fun <T> unique(code: String, block: () -> T): T =
    try {
        block()
    } catch (_: DuplicateKeyException) {
        throw CatalogConflict(code)
    }

@Repository
class JdbcServiceGroupRepository(private val jdbc: JdbcClient, private val clinic: ClinicContext) : ServiceGroupRepository {
    override fun insert(group: ServiceGroup) {
        val identity = clinic.current()
        unique("catalog.group_name_taken") {
            jdbc.sql(
                """
                insert into service_group (id, clinic_id, branch_id, name, sort_order, created_at, updated_at, version)
                values (:id, :clinicId, :branchId, :name, :sortOrder, :createdAt, :updatedAt, 0)
                """,
            ).param("id", group.id).param("clinicId", identity.clinicId).param("branchId", identity.branchId)
                .param("name", group.name).param("sortOrder", group.sortOrder)
                .param("createdAt", group.createdAt.toDb()).param("updatedAt", group.updatedAt.toDb()).update()
        }
    }

    override fun findById(id: UUID): ServiceGroup? =
        jdbc.sql("select * from service_group where id = :id and deleted_at is null").param("id", id).query(::map).optional().orElse(null)

    override fun findAll(): List<ServiceGroup> =
        jdbc.sql("select * from service_group where deleted_at is null order by sort_order, lower(name), id").query(::map).list()

    override fun update(group: ServiceGroup): ServiceGroup {
        val rows = unique("catalog.group_name_taken") {
            jdbc.sql(
                """
                update service_group set name = :name, sort_order = :sortOrder, updated_at = :updatedAt, deleted_at = :deletedAt,
                       version = version + 1
                where id = :id and version = :version and deleted_at is null
                """,
            ).param("id", group.id).param("name", group.name).param("sortOrder", group.sortOrder)
                .param("updatedAt", group.updatedAt.toDb()).param("deletedAt", group.deletedAt.toDbOrNull())
                .param("version", group.version).update()
        }
        requireVersionMatched(rows, "service_group", group.id)
        return group.copy(version = group.version + 1)
    }

    override fun countServices(groupId: UUID): Long =
        jdbc.sql("select count(*) from service_item where group_id = :id and deleted_at is null").param("id", groupId)
            .query(Long::class.java).single()

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = ServiceGroup(
        id = rs.uuid("id"),
        name = rs.getString("name"),
        sortOrder = rs.getInt("sort_order"),
        createdAt = rs.instant("created_at"),
        updatedAt = rs.instant("updated_at"),
        deletedAt = rs.instantOrNull("deleted_at"),
        version = rs.getInt("version"),
    )
}

@Repository
class JdbcServiceItemRepository(private val jdbc: JdbcClient, private val clinic: ClinicContext) : ServiceItemRepository {
    override fun insert(item: ServiceItem) {
        val identity = clinic.current()
        unique(conflictCode(item)) {
            jdbc.sql(
                """
                insert into service_item (id, clinic_id, branch_id, group_id, code, name, price, per_doctor, active,
                                          created_at, updated_at, version)
                values (:id, :clinicId, :branchId, :groupId, :code, :name, :price, :perDoctor, :active, :createdAt, :updatedAt, 0)
                """,
            ).params(params(item)).param("clinicId", identity.clinicId).param("branchId", identity.branchId)
                .param("createdAt", item.createdAt.toDb()).update()
        }
        writeDoctorPrices(item)
    }

    override fun findById(id: UUID): ServiceItem? =
        jdbc.sql("select * from service_item where id = :id and deleted_at is null").param("id", id).query(::map).optional().orElse(null)
            ?.let { withDoctorPrices(listOf(it)).single() }

    override fun findAll(): List<ServiceItem> =
        withDoctorPrices(jdbc.sql("select * from service_item where deleted_at is null order by lower(name), id").query(::map).list())

    override fun update(item: ServiceItem): ServiceItem {
        val rows = unique(conflictCode(item)) {
            jdbc.sql(
                """
                update service_item
                set group_id = :groupId, code = :code, name = :name, price = :price, per_doctor = :perDoctor, active = :active,
                    updated_at = :updatedAt, deleted_at = :deletedAt, version = version + 1
                where id = :id and version = :version and deleted_at is null
                """,
            ).params(params(item)).param("deletedAt", item.deletedAt.toDbOrNull()).param("version", item.version).update()
        }
        requireVersionMatched(rows, "service_item", item.id)
        writeDoctorPrices(item)
        return item.copy(version = item.version + 1)
    }

    /** Which unique index fired is not reported portably; a code is set only when the Admin typed one. */
    private fun conflictCode(item: ServiceItem) = if (item.details.code != null) "catalog.service_code_or_name_taken" else "catalog.service_name_taken"

    private fun writeDoctorPrices(item: ServiceItem) {
        jdbc.sql("delete from service_doctor_price where service_id = :id").param("id", item.id).update()
        item.details.doctorPrices.forEach { (staffId, price) ->
            jdbc.sql("insert into service_doctor_price (service_id, staff_id, price) values (:service, :staff, :price)")
                .param("service", item.id).param("staff", staffId).param("price", price).update()
        }
    }

    private fun withDoctorPrices(items: List<ServiceItem>): List<ServiceItem> {
        if (items.isEmpty()) return items
        val prices = jdbc.sql("select * from service_doctor_price where service_id = any(:ids)")
            .param("ids", items.map { it.id }.toTypedArray())
            .query { rs, _ -> Triple(rs.uuid("service_id"), rs.uuid("staff_id"), rs.getLong("price")) }.list()
            .groupBy({ it.first }, { it.second to it.third })
        return items.map { item -> prices[item.id]?.let { item.copy(details = item.details.copy(doctorPrices = it.toMap())) } ?: item }
    }

    private fun params(i: ServiceItem) = mapOf(
        "id" to i.id,
        "groupId" to i.details.groupId,
        "code" to i.details.code,
        "name" to i.details.name,
        "price" to i.details.price,
        "perDoctor" to i.details.perDoctor,
        "active" to i.details.active,
        "updatedAt" to i.updatedAt.toDb(),
    )

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = ServiceItem(
        id = rs.uuid("id"),
        details = ServiceDetails(
            groupId = rs.uuid("group_id"),
            code = rs.getString("code"),
            name = rs.getString("name"),
            price = rs.getLong("price"),
            perDoctor = rs.getBoolean("per_doctor"),
            doctorPrices = emptyMap(),
            active = rs.getBoolean("active"),
        ),
        createdAt = rs.instant("created_at"),
        updatedAt = rs.instant("updated_at"),
        deletedAt = rs.instantOrNull("deleted_at"),
        version = rs.getInt("version"),
    )
}

@Repository
class JdbcDiscountTypeRepository(private val jdbc: JdbcClient, private val clinic: ClinicContext) : DiscountTypeRepository {
    override fun insert(discount: DiscountType) {
        val identity = clinic.current()
        unique("catalog.discount_name_taken") {
            jdbc.sql(
                """
                insert into discount_type (id, clinic_id, branch_id, name, percent, active, created_at, updated_at, version)
                values (:id, :clinicId, :branchId, :name, :percent, :active, :createdAt, :updatedAt, 0)
                """,
            ).param("id", discount.id).param("clinicId", identity.clinicId).param("branchId", identity.branchId)
                .param("name", discount.name).param("percent", discount.percent).param("active", discount.active)
                .param("createdAt", discount.createdAt.toDb()).param("updatedAt", discount.updatedAt.toDb()).update()
        }
    }

    override fun findById(id: UUID): DiscountType? =
        jdbc.sql("select * from discount_type where id = :id and deleted_at is null").param("id", id).query(::map).optional().orElse(null)

    override fun findAll(): List<DiscountType> =
        jdbc.sql("select * from discount_type where deleted_at is null order by lower(name), id").query(::map).list()

    override fun update(discount: DiscountType): DiscountType {
        val rows = unique("catalog.discount_name_taken") {
            jdbc.sql(
                """
                update discount_type set name = :name, percent = :percent, active = :active, updated_at = :updatedAt,
                       deleted_at = :deletedAt, version = version + 1
                where id = :id and version = :version and deleted_at is null
                """,
            ).param("id", discount.id).param("name", discount.name).param("percent", discount.percent)
                .param("active", discount.active).param("updatedAt", discount.updatedAt.toDb())
                .param("deletedAt", discount.deletedAt.toDbOrNull()).param("version", discount.version).update()
        }
        requireVersionMatched(rows, "discount_type", discount.id)
        return discount.copy(version = discount.version + 1)
    }

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = DiscountType(
        id = rs.uuid("id"),
        name = rs.getString("name"),
        percent = rs.getInt("percent"),
        active = rs.getBoolean("active"),
        createdAt = rs.instant("created_at"),
        updatedAt = rs.instant("updated_at"),
        deletedAt = rs.instantOrNull("deleted_at"),
        version = rs.getInt("version"),
    )
}
