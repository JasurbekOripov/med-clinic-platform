package uz.medclinic.clinic.lab.web

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
import uz.medclinic.clinic.lab.application.LabTemplateService
import uz.medclinic.clinic.lab.domain.FormRow
import uz.medclinic.clinic.lab.domain.LabTemplate
import uz.medclinic.clinic.shared.Permissions
import uz.medclinic.platform.domain.ValidationException
import java.time.Instant
import java.util.UUID

data class LabTemplateResponse(
    val id: UUID,
    val name: String,
    val serviceIds: List<UUID>,
    val rows: List<FormRow>,
    val versionNo: Int,
    val active: Boolean,
    val updatedAt: Instant,
    val version: Int,
) {
    constructor(t: LabTemplate) : this(t.id, t.name, t.serviceIds.sorted(), t.rows, t.versionNo, t.active, t.updatedAt, t.version)
}

data class LabTemplateRequest(
    @field:Size(max = 200) val name: String?,
    @field:Size(max = 50) val serviceIds: Set<UUID> = emptySet(),
    @field:Size(max = 200) val rows: List<FormRow> = emptyList(),
    val active: Boolean = true,
    val version: Int? = null,
)

/** Lab result forms: the lab (LAB_TEMPLATES_MANAGE) creates them; lab workers read them to enter results. */
@RestController
@RequestMapping("/api/v1/lab/templates")
class LabTemplateController(private val templates: LabTemplateService) {
    @GetMapping
    @PreAuthorize(Permissions.HAS_LAB_READ)
    fun list() = templates.list().map(::LabTemplateResponse)

    @GetMapping("/{id}")
    @PreAuthorize(Permissions.HAS_LAB_READ)
    fun get(@PathVariable id: UUID) = LabTemplateResponse(templates.get(id))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.HAS_LAB_TEMPLATES_MANAGE)
    fun create(@Valid @RequestBody body: LabTemplateRequest) = LabTemplateResponse(templates.create(body.name, body.serviceIds, body.rows, body.active))

    @PutMapping("/{id}")
    @PreAuthorize(Permissions.HAS_LAB_TEMPLATES_MANAGE)
    fun update(@PathVariable id: UUID, @Valid @RequestBody body: LabTemplateRequest) = LabTemplateResponse(
        templates.update(id, body.version ?: throw ValidationException("version", "validation.not_null"), body.name, body.serviceIds, body.rows, body.active),
    )

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Permissions.HAS_LAB_TEMPLATES_MANAGE)
    fun delete(@PathVariable id: UUID, @RequestParam version: Int) = templates.delete(id, version)
}
