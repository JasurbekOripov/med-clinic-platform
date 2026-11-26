package uz.medclinic.clinic.agents.api

import java.util.UUID

/** What visits need to record a referral: the agent and the fee owed for one paid visit. */
data class AgentSummary(val id: UUID, val name: String, val fee: Long, val active: Boolean)

interface AgentQueries {
    /** Null for unknown or deleted agents. */
    fun find(id: UUID): AgentSummary?
}
