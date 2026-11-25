package uz.medclinic.vendor.audit.infrastructure

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import uz.medclinic.vendor.audit.application.AuditEntry
import uz.medclinic.vendor.audit.application.AuditFilter
import uz.medclinic.platform.domain.Page
import uz.medclinic.platform.domain.PageQuery
import uz.medclinic.platform.persistence.instant
import uz.medclinic.platform.persistence.toDb
import uz.medclinic.platform.persistence.uuidOrNull
import uz.medclinic.platform.persistence.uuid
import java.sql.ResultSet

@Repository
class AuditRepository(private val jdbc: JdbcClient) {
    fun insert(entry: AuditEntry) {
        jdbc.sql(
            """
            insert into audit_log (id, occurred_at, actor_type, actor_id, actor_name, action, entity_type, entity_id,
                                   clinic_id, before, after, ip, user_agent, request_id)
            values (:id, :occurredAt, :actorType, :actorId, :actorName, :action, :entityType, :entityId,
                    :clinicId, cast(:before as jsonb), cast(:after as jsonb), :ip, :userAgent, :requestId)
            """,
        )
            .param("id", entry.id)
            .param("occurredAt", entry.occurredAt.toDb())
            .param("actorType", entry.actorType)
            .param("actorId", entry.actorId)
            .param("actorName", entry.actorName)
            .param("action", entry.action)
            .param("entityType", entry.entityType)
            .param("entityId", entry.entityId)
            .param("clinicId", entry.clinicId)
            .param("before", entry.before)
            .param("after", entry.after)
            .param("ip", entry.ip)
            .param("userAgent", entry.userAgent)
            .param("requestId", entry.requestId)
            .update()
    }

    fun search(filter: AuditFilter, page: PageQuery): Page<AuditEntry> {
        val conditions = mutableListOf<String>()
        val params = mutableMapOf<String, Any?>()
        filter.entityType?.let { conditions += "entity_type = :entityType"; params["entityType"] = it }
        filter.entityId?.let { conditions += "entity_id = :entityId"; params["entityId"] = it }
        filter.actorId?.let { conditions += "actor_id = :actorId"; params["actorId"] = it }
        filter.clinicId?.let { conditions += "clinic_id = :clinicId"; params["clinicId"] = it }
        filter.action?.let { conditions += "action = :action"; params["action"] = it }
        filter.from?.let { conditions += "occurred_at >= :from"; params["from"] = it.toDb() }
        filter.to?.let { conditions += "occurred_at < :to"; params["to"] = it.toDb() }
        val where = if (conditions.isEmpty()) "" else "where " + conditions.joinToString(" and ")

        val total = jdbc.sql("select count(*) from audit_log $where").params(params).query(Long::class.java).single()
        val items = jdbc.sql(
            """
            select id, occurred_at, actor_type, actor_id, actor_name, action, entity_type, entity_id, clinic_id,
                   before::text as before, after::text as after, ip, user_agent, request_id
            from audit_log $where
            order by occurred_at desc, id desc
            limit :limit offset :offset
            """,
        ).params(params).param("limit", page.size).param("offset", page.offset).query(::map).list()
        return Page(items, page.page, page.size, total)
    }

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = AuditEntry(
        id = rs.uuid("id"),
        occurredAt = rs.instant("occurred_at"),
        actorType = rs.getString("actor_type"),
        actorId = rs.uuidOrNull("actor_id"),
        actorName = rs.getString("actor_name"),
        action = rs.getString("action"),
        entityType = rs.getString("entity_type"),
        entityId = rs.uuidOrNull("entity_id"),
        clinicId = rs.uuidOrNull("clinic_id"),
        before = rs.getString("before"),
        after = rs.getString("after"),
        ip = rs.getString("ip"),
        userAgent = rs.getString("user_agent"),
        requestId = rs.getString("request_id"),
    )
}
