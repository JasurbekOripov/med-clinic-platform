package uz.medclinic.clinic.agents.infrastructure

import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import uz.medclinic.clinic.agents.domain.Agent
import uz.medclinic.clinic.agents.domain.AgentDetails
import uz.medclinic.clinic.agents.domain.AgentRepository
import uz.medclinic.clinic.shared.ClinicContext
import uz.medclinic.common.UzPhone
import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.persistence.instant
import uz.medclinic.platform.persistence.instantOrNull
import uz.medclinic.platform.persistence.requireVersionMatched
import uz.medclinic.platform.persistence.toDb
import uz.medclinic.platform.persistence.toDbOrNull
import uz.medclinic.platform.persistence.uuid
import java.sql.ResultSet
import java.util.UUID

@Repository
class JdbcAgentRepository(private val jdbc: JdbcClient, private val clinic: ClinicContext) : AgentRepository {
    override fun insert(agent: Agent) {
        val identity = clinic.current()
        unique {
            jdbc.sql(
                """
                insert into agent (id, clinic_id, branch_id, name, phone, fee, notes, active, created_at, updated_at, version)
                values (:id, :clinicId, :branchId, :name, :phone, :fee, :notes, :active, :createdAt, :updatedAt, 0)
                """,
            ).params(params(agent)).param("clinicId", identity.clinicId).param("branchId", identity.branchId)
                .param("createdAt", agent.createdAt.toDb()).update()
        }
    }

    override fun findById(id: UUID): Agent? =
        jdbc.sql("select * from agent where id = :id and deleted_at is null").param("id", id).query(::map).optional().orElse(null)

    override fun findAll(): List<Agent> =
        jdbc.sql("select * from agent where deleted_at is null order by lower(name), id").query(::map).list()

    override fun update(agent: Agent): Agent {
        val rows = unique {
            jdbc.sql(
                """
                update agent set name = :name, phone = :phone, fee = :fee, notes = :notes, active = :active,
                       updated_at = :updatedAt, deleted_at = :deletedAt, version = version + 1
                where id = :id and version = :version and deleted_at is null
                """,
            ).params(params(agent)).param("deletedAt", agent.deletedAt.toDbOrNull()).param("version", agent.version).update()
        }
        requireVersionMatched(rows, "agent", agent.id)
        return agent.copy(version = agent.version + 1)
    }

    private inline fun <T> unique(block: () -> T): T =
        try {
            block()
        } catch (_: DuplicateKeyException) {
            throw DomainException("agent.name_taken", DomainException.Kind.CONFLICT)
        }

    private fun params(a: Agent) = mapOf(
        "id" to a.id,
        "name" to a.details.name,
        "phone" to a.details.phone?.e164,
        "fee" to a.details.fee,
        "notes" to a.details.notes,
        "active" to a.details.active,
        "updatedAt" to a.updatedAt.toDb(),
    )

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = Agent(
        id = rs.uuid("id"),
        details = AgentDetails(
            name = rs.getString("name"),
            phone = rs.getString("phone")?.let(UzPhone::of),
            fee = rs.getLong("fee"),
            notes = rs.getString("notes"),
            active = rs.getBoolean("active"),
        ),
        createdAt = rs.instant("created_at"),
        updatedAt = rs.instant("updated_at"),
        deletedAt = rs.instantOrNull("deleted_at"),
        version = rs.getInt("version"),
    )
}
