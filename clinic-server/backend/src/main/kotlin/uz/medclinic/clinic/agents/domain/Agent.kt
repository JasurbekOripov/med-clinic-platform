package uz.medclinic.clinic.agents.domain

import uz.medclinic.common.UzPhone
import uz.medclinic.platform.domain.FieldViolation
import uz.medclinic.platform.domain.Text
import uz.medclinic.platform.domain.ValidationException
import java.time.Instant
import java.util.UUID

/** Same sanity limit as prices: one fee can never exceed 1 billion UZS. */
const val MAX_FEE: Long = 1_000_000_000

data class AgentDetails(val name: String, val phone: UzPhone?, val fee: Long, val notes: String?, val active: Boolean) {
    companion object {
        fun of(name: String?, phone: String?, fee: Long?, notes: String?, active: Boolean): AgentDetails {
            val errors = mutableListOf<FieldViolation>()
            val cleanName = Text.clean(name)
            if (cleanName == null) errors += FieldViolation("name", "validation.not_blank")
            else if (cleanName.length > 200) errors += FieldViolation("name", "validation.size", mapOf("max" to 200))
            val cleanPhone = Text.clean(phone)
            val parsedPhone = cleanPhone?.let { UzPhone.parse(it) ?: run { errors += FieldViolation("phone", "validation.phone"); null } }
            when {
                fee == null -> errors += FieldViolation("fee", "validation.not_null")
                fee < 0 || fee > MAX_FEE -> errors += FieldViolation("fee", "validation.range", mapOf("min" to 0, "max" to MAX_FEE))
            }
            val cleanNotes = Text.cleanMultiline(notes)
            if (cleanNotes != null && cleanNotes.length > 1000) errors += FieldViolation("notes", "validation.size", mapOf("max" to 1000))
            if (errors.isNotEmpty()) throw ValidationException(errors)
            return AgentDetails(cleanName!!, parsedPhone, fee!!, cleanNotes, active)
        }
    }
}

data class Agent(
    val id: UUID,
    val details: AgentDetails,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val version: Int,
) {
    fun withDetails(details: AgentDetails, now: Instant) = copy(details = details, updatedAt = now)

    fun deleted(now: Instant) = copy(deletedAt = now, updatedAt = now)
}

interface AgentRepository {
    fun insert(agent: Agent)
    fun findById(id: UUID): Agent?
    fun findAll(): List<Agent>
    fun update(agent: Agent): Agent
}
