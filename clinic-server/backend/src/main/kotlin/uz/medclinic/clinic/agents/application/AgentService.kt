package uz.medclinic.clinic.agents.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.clinic.agents.api.AgentQueries
import uz.medclinic.clinic.agents.api.AgentSummary
import uz.medclinic.clinic.agents.domain.Agent
import uz.medclinic.clinic.agents.domain.AgentDetails
import uz.medclinic.clinic.agents.domain.AgentRepository
import uz.medclinic.clinic.audit.api.AuditEvent
import uz.medclinic.clinic.audit.api.AuditRecorder
import uz.medclinic.common.Ids
import uz.medclinic.platform.domain.NotFoundException
import uz.medclinic.platform.domain.VersionConflictException
import java.time.Clock
import java.util.UUID

@Service
class AgentService(
    private val agents: AgentRepository,
    private val audit: AuditRecorder,
    private val clock: Clock,
) : AgentQueries {
    @Transactional(readOnly = true)
    fun list(): List<Agent> = agents.findAll()

    @Transactional
    fun create(details: AgentDetails): Agent {
        val now = clock.instant()
        val agent = Agent(Ids.newId(), details, now, now, null, 0)
        agents.insert(agent)
        audit.record(AuditEvent("agent.created", ENTITY, agent.id, after = snapshot(agent)))
        return agent
    }

    @Transactional
    fun update(id: UUID, version: Int, details: AgentDetails): Agent {
        val current = load(id, version)
        val updated = agents.update(current.withDetails(details, clock.instant()))
        audit.record(AuditEvent("agent.updated", ENTITY, id, before = snapshot(current), after = snapshot(updated)))
        return updated
    }

    /** Soft delete: referrals already recorded on visits keep the agent's name and fee. */
    @Transactional
    fun delete(id: UUID, version: Int) {
        val current = load(id, version)
        agents.update(current.deleted(clock.instant()))
        audit.record(AuditEvent("agent.deleted", ENTITY, id, before = snapshot(current)))
    }

    @Transactional(readOnly = true)
    override fun find(id: UUID): AgentSummary? = agents.findById(id)?.let { AgentSummary(it.id, it.details.name, it.details.fee, it.details.active) }

    private fun load(id: UUID, version: Int): Agent {
        val agent = agents.findById(id) ?: throw NotFoundException(ENTITY, id)
        if (agent.version != version) throw VersionConflictException(ENTITY, id)
        return agent
    }

    // Phone and notes are not personal data of patients; still, only what the Admin needs to trace a change.
    private fun snapshot(a: Agent) = mapOf("name" to a.details.name, "phone" to a.details.phone?.e164, "fee" to a.details.fee, "active" to a.details.active)

    private companion object {
        const val ENTITY = "agent"
    }
}
