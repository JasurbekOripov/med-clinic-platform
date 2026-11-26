package uz.medclinic.clinic.visits.infrastructure

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import uz.medclinic.clinic.shared.ClinicContext
import uz.medclinic.clinic.visits.domain.Allowance
import uz.medclinic.clinic.visits.domain.Cancellation
import uz.medclinic.clinic.visits.domain.ItemStatus
import uz.medclinic.clinic.visits.domain.Visit
import uz.medclinic.clinic.visits.domain.VisitAgent
import uz.medclinic.clinic.visits.domain.VisitDiscount
import uz.medclinic.clinic.visits.domain.VisitItem
import uz.medclinic.clinic.visits.domain.VisitRepository
import uz.medclinic.platform.persistence.instant
import uz.medclinic.platform.persistence.instantOrNull
import uz.medclinic.platform.persistence.longOrNull
import uz.medclinic.platform.persistence.requireVersionMatched
import uz.medclinic.platform.persistence.toDb
import uz.medclinic.platform.persistence.toDbOrNull
import uz.medclinic.platform.persistence.uuid
import uz.medclinic.platform.persistence.uuidOrNull
import java.sql.ResultSet
import java.util.UUID

@Repository
class JdbcVisitRepository(private val jdbc: JdbcClient, private val clinic: ClinicContext) : VisitRepository {
    override fun insert(visit: Visit): Visit {
        val identity = clinic.current()
        val visitNo = jdbc.sql(
            """
            insert into visit (id, clinic_id, branch_id, visit_no, patient_id, discount_type_id, discount_name, discount_percent,
                               agent_id, agent_name, agent_fee, created_by, created_at, updated_at, version)
            values (:id, :clinicId, :branchId, nextval('visit_no_seq'), :patientId, :discountTypeId, :discountName,
                    :discountPercent, :agentId, :agentName, :agentFee, :createdBy, :createdAt, :updatedAt, 0)
            returning visit_no
            """,
        ).params(visitParams(visit)).param("clinicId", identity.clinicId).param("branchId", identity.branchId)
            .param("patientId", visit.patientId).param("createdBy", visit.createdBy).param("createdAt", visit.createdAt.toDb())
            .query(Long::class.java).single()
        visit.items.forEach { upsertItem(visit.id, it) }
        return visit.copy(visitNo = visitNo)
    }

    override fun findById(id: UUID): Visit? = load("select * from visit where id = :id", id)

    override fun lock(id: UUID): Visit? = load("select * from visit where id = :id for update", id)

    override fun update(visit: Visit): Visit {
        val rows = jdbc.sql(
            """
            update visit set discount_type_id = :discountTypeId, discount_name = :discountName, discount_percent = :discountPercent,
                   agent_id = :agentId, agent_name = :agentName, agent_fee = :agentFee, updated_at = :updatedAt, version = version + 1
            where id = :id and version = :version
            """,
        ).params(visitParams(visit)).param("version", visit.version).update()
        requireVersionMatched(rows, "visit", visit.id)
        visit.items.forEach { upsertItem(visit.id, it) }
        return visit.copy(version = visit.version + 1)
    }

    override fun findByPatient(patientId: UUID, limit: Int): List<Visit> =
        withItems(
            jdbc.sql("select * from visit where patient_id = :patientId order by created_at desc, id desc limit :limit")
                .param("patientId", patientId).param("limit", limit).query(::mapVisit).list(),
        )

    override fun findByItem(itemId: UUID): Visit? =
        jdbc.sql("select v.* from visit v join visit_item i on i.visit_id = v.id where i.id = :id").param("id", itemId)
            .query(::mapVisit).optional().orElse(null)?.let { withItems(listOf(it)).single() }

    override fun findUnpaid(offset: Long, limit: Int): Pair<List<Visit>, Long> {
        val where = "exists (select 1 from visit_item i where i.visit_id = v.id and i.status = 'UNPAID')"
        val total = jdbc.sql("select count(*) from visit v where $where").query(Long::class.java).single()
        val visits = jdbc.sql("select v.* from visit v where $where order by v.created_at desc, v.id desc limit :limit offset :offset")
            .param("limit", limit).param("offset", offset).query(::mapVisit).list()
        return withItems(visits) to total
    }

    private fun load(sql: String, id: UUID): Visit? =
        jdbc.sql(sql).param("id", id).query(::mapVisit).optional().orElse(null)?.let { withItems(listOf(it)).single() }

    /** Items are part of the visit aggregate: new ones are inserted, existing ones updated in place (never deleted). */
    private fun upsertItem(visitId: UUID, item: VisitItem) {
        jdbc.sql(
            """
            insert into visit_item (id, visit_id, service_id, service_name, group_name, doctor_id, doctor_name, price, discount, status,
                                    payment_id, paid_at, allowed_by, allowed_at, allow_reason, cancelled_by, cancelled_at, cancel_reason,
                                    created_by, created_at)
            values (:id, :visitId, :serviceId, :serviceName, :groupName, :doctorId, :doctorName, :price, :discount, :status,
                    :paymentId, :paidAt, :allowedBy, :allowedAt, :allowReason, :cancelledBy, :cancelledAt, :cancelReason,
                    :createdBy, :createdAt)
            on conflict (id) do update set
                discount = excluded.discount, status = excluded.status, payment_id = excluded.payment_id, paid_at = excluded.paid_at,
                allowed_by = excluded.allowed_by, allowed_at = excluded.allowed_at, allow_reason = excluded.allow_reason,
                cancelled_by = excluded.cancelled_by, cancelled_at = excluded.cancelled_at, cancel_reason = excluded.cancel_reason
            """,
        )
            .param("id", item.id).param("visitId", visitId).param("serviceId", item.serviceId)
            .param("serviceName", item.serviceName).param("groupName", item.groupName).param("doctorId", item.doctorId)
            .param("doctorName", item.doctorName).param("price", item.price).param("discount", item.discount)
            .param("status", item.status.name).param("paymentId", item.paymentId).param("paidAt", item.paidAt.toDbOrNull())
            .param("allowedBy", item.allowance?.by).param("allowedAt", item.allowance?.at.toDbOrNull())
            .param("allowReason", item.allowance?.reason).param("cancelledBy", item.cancellation?.by)
            .param("cancelledAt", item.cancellation?.at.toDbOrNull()).param("cancelReason", item.cancellation?.reason)
            .param("createdBy", item.createdBy).param("createdAt", item.createdAt.toDb())
            .update()
    }

    private fun withItems(visits: List<Visit>): List<Visit> {
        if (visits.isEmpty()) return visits
        val items = jdbc.sql("select * from visit_item where visit_id = any(:ids) order by created_at, id")
            .param("ids", visits.map { it.id }.toTypedArray())
            .query { rs, _ -> rs.uuid("visit_id") to mapItem(rs) }.list()
            .groupBy({ it.first }, { it.second })
        return visits.map { it.copy(items = items[it.id].orEmpty()) }
    }

    private fun visitParams(v: Visit) = mapOf(
        "id" to v.id,
        "discountTypeId" to v.discount?.typeId,
        "discountName" to v.discount?.name,
        "discountPercent" to (v.discount?.percent ?: 0),
        "agentId" to v.agent?.id,
        "agentName" to v.agent?.name,
        "agentFee" to v.agent?.fee,
        "updatedAt" to v.updatedAt.toDb(),
    )

    private fun mapVisit(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int): Visit {
        val discountType = rs.uuidOrNull("discount_type_id")
        val agentId = rs.uuidOrNull("agent_id")
        return Visit(
            id = rs.uuid("id"),
            visitNo = rs.getLong("visit_no"),
            patientId = rs.uuid("patient_id"),
            discount = discountType?.let { VisitDiscount(it, rs.getString("discount_name"), rs.getInt("discount_percent")) },
            agent = agentId?.let { VisitAgent(it, rs.getString("agent_name"), rs.longOrNull("agent_fee") ?: 0) },
            items = emptyList(),
            createdBy = rs.uuidOrNull("created_by"),
            createdAt = rs.instant("created_at"),
            updatedAt = rs.instant("updated_at"),
            version = rs.getInt("version"),
        )
    }

    private fun mapItem(rs: ResultSet) = VisitItem(
        id = rs.uuid("id"),
        serviceId = rs.uuid("service_id"),
        serviceName = rs.getString("service_name"),
        groupName = rs.getString("group_name"),
        doctorId = rs.uuidOrNull("doctor_id"),
        doctorName = rs.getString("doctor_name"),
        price = rs.getLong("price"),
        discount = rs.getLong("discount"),
        status = ItemStatus.valueOf(rs.getString("status")),
        paymentId = rs.uuidOrNull("payment_id"),
        paidAt = rs.instantOrNull("paid_at"),
        allowance = rs.uuidOrNull("allowed_by")?.let { Allowance(it, rs.instant("allowed_at"), rs.getString("allow_reason")) },
        cancellation = rs.instantOrNull("cancelled_at")?.let { Cancellation(rs.uuidOrNull("cancelled_by"), it, rs.getString("cancel_reason")) },
        createdBy = rs.uuidOrNull("created_by"),
        createdAt = rs.instant("created_at"),
    )
}
