package uz.medclinic.clinic.conclusions.web

import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
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
import uz.medclinic.clinic.conclusions.application.ConclusionService
import uz.medclinic.clinic.conclusions.domain.Conclusion
import uz.medclinic.clinic.conclusions.domain.ConclusionTemplate
import uz.medclinic.clinic.conclusions.domain.ConclusionText
import uz.medclinic.clinic.conclusions.domain.MAX_BODY
import uz.medclinic.clinic.shared.Permissions
import uz.medclinic.platform.domain.ValidationException
import uz.medclinic.platform.security.CurrentActor
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class ConclusionNoteResponse(val id: UUID, val authorName: String, val text: String, val createdAt: Instant)

data class ConclusionResponse(
    val id: UUID,
    val patientId: UUID,
    val visitId: UUID,
    val visitItemId: UUID,
    val authorId: UUID,
    val authorName: String,
    val title: String,
    val body: String,
    val notes: List<ConclusionNoteResponse>,
    /** The signed-in user wrote it today and may still change the text. */
    val editable: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Int,
)

data class WriteConclusionRequest(val visitItemId: UUID?, @field:Size(max = 200) val title: String?, @field:Size(max = MAX_BODY) val body: String?)

data class EditConclusionRequest(@field:Size(max = 200) val title: String?, @field:Size(max = MAX_BODY) val body: String?, val version: Int?)

data class NoteRequest(@field:Size(max = 5000) val text: String?)

data class TemplateResponse(val id: UUID, val name: String, val body: String, val version: Int) {
    constructor(t: ConclusionTemplate) : this(t.id, t.name, t.body, t.version)
}

data class TemplateRequest(@field:Size(max = 100) val name: String?, @field:Size(max = MAX_BODY) val body: String?, val version: Int? = null)

@RestController
@RequestMapping("/api/v1/conclusions")
class ConclusionController(private val conclusions: ConclusionService) {
    @GetMapping
    @PreAuthorize(Permissions.HAS_CONCLUSION_READ)
    fun byPatient(@RequestParam patientId: UUID) = conclusions.byPatient(patientId).map(::response)

    @GetMapping("/{id}")
    @PreAuthorize(Permissions.HAS_CONCLUSION_READ)
    fun get(@PathVariable id: UUID) = response(conclusions.get(id))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.HAS_CONSULTATION_WRITE)
    fun write(@Valid @RequestBody body: WriteConclusionRequest) = response(
        conclusions.write(body.visitItemId ?: throw ValidationException("visitItemId", "validation.not_null"), ConclusionText.of(body.title, body.body)),
    )

    @PutMapping("/{id}")
    @PreAuthorize(Permissions.HAS_CONSULTATION_WRITE)
    fun edit(@PathVariable id: UUID, @Valid @RequestBody body: EditConclusionRequest) = response(
        conclusions.edit(id, body.version ?: throw ValidationException("version", "validation.not_null"), ConclusionText.of(body.title, body.body)),
    )

    @PostMapping("/{id}/notes")
    @PreAuthorize(Permissions.HAS_CONSULTATION_WRITE)
    fun addNote(@PathVariable id: UUID, @Valid @RequestBody body: NoteRequest) = response(conclusions.addNote(id, body.text))

    /** The author's signature image for the printed conclusion. */
    @GetMapping("/{id}/signature")
    @PreAuthorize(Permissions.HAS_CONCLUSION_READ)
    fun signature(@PathVariable id: UUID): ResponseEntity<ByteArray> {
        val (bytes, type) = conclusions.signature(id)
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type))
            .cacheControl(CacheControl.maxAge(Duration.ZERO).cachePrivate().mustRevalidate()).body(bytes)
    }

    @GetMapping("/templates")
    @PreAuthorize(Permissions.HAS_CONSULTATION_WRITE)
    fun templates() = conclusions.myTemplates().map(::TemplateResponse)

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.HAS_CONSULTATION_WRITE)
    fun createTemplate(@Valid @RequestBody body: TemplateRequest) = TemplateResponse(conclusions.saveTemplate(null, null, body.name, body.body))

    @PutMapping("/templates/{id}")
    @PreAuthorize(Permissions.HAS_CONSULTATION_WRITE)
    fun updateTemplate(@PathVariable id: UUID, @Valid @RequestBody body: TemplateRequest) =
        TemplateResponse(conclusions.saveTemplate(id, body.version, body.name, body.body))

    @DeleteMapping("/templates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Permissions.HAS_CONSULTATION_WRITE)
    fun deleteTemplate(@PathVariable id: UUID, @RequestParam version: Int) = conclusions.deleteTemplate(id, version)

    private fun response(c: Conclusion): ConclusionResponse {
        val me = CurrentActor.get().actorId
        return ConclusionResponse(
            c.id, c.patientId, c.visitId, c.visitItemId, c.authorId, c.authorName, c.text.title, c.text.body,
            c.notes.map { ConclusionNoteResponse(it.id, it.authorName, it.text, it.createdAt) },
            me != null && c.editableBy(me, conclusions.today(), conclusions.writtenOn(c)), c.createdAt, c.updatedAt, c.version,
        )
    }
}
