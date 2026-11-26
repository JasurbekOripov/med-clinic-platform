package uz.medclinic.clinic.agents.web

import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uz.medclinic.clinic.agents.application.AgentService
import uz.medclinic.clinic.agents.domain.Agent
import uz.medclinic.clinic.agents.domain.AgentDetails
import uz.medclinic.clinic.shared.Permissions
import uz.medclinic.platform.domain.ValidationException
import java.util.UUID

data class AgentResponse(val id: UUID, val name: String, val phone: String?, val fee: Long, val notes: String?, val active: Boolean, val version: Int) {
    constructor(a: Agent) : this(a.id, a.details.name, a.details.phone?.e164, a.details.fee, a.details.notes, a.details.active, a.version)
}

data class AgentRequest(
    @field:Size(max = 200) val name: String?,
    @field:Size(max = 32) val phone: String?,
    val fee: Long?,
    @field:Size(max = 1000) val notes: String?,
    val active: Boolean = true,
    val version: Int? = null,
)

@RestController
@RequestMapping("/api/v1/agents")
class AgentController(private val agents: AgentService) {
    @GetMapping
    @PreAuthorize(Permissions.HAS_AGENT_READ)
    fun list() = agents.list().map(::AgentResponse)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.HAS_AGENT_MANAGE)
    fun create(@Valid @RequestBody body: AgentRequest) = AgentResponse(agents.create(details(body)))

    @PutMapping("/{id}")
    @PreAuthorize(Permissions.HAS_AGENT_MANAGE)
    fun update(@PathVariable id: UUID, @Valid @RequestBody body: AgentRequest) =
        AgentResponse(agents.update(id, body.version ?: throw ValidationException("version", "validation.not_null"), details(body)))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Permissions.HAS_AGENT_MANAGE)
    fun delete(@PathVariable id: UUID, @RequestParam version: Int) = agents.delete(id, version)

    private fun details(b: AgentRequest) = AgentDetails.of(b.name, b.phone, b.fee, b.notes, b.active)
}
