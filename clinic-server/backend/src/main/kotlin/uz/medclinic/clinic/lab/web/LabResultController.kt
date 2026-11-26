package uz.medclinic.clinic.lab.web

import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uz.medclinic.clinic.lab.application.LabResultService
import uz.medclinic.clinic.lab.application.ResultView
import uz.medclinic.clinic.lab.domain.Flag
import uz.medclinic.clinic.lab.domain.FormRow
import uz.medclinic.clinic.lab.domain.LabResult
import uz.medclinic.clinic.lab.domain.PhysioState
import uz.medclinic.clinic.lab.domain.ResultStatus
import uz.medclinic.clinic.lab.domain.RowValue
import uz.medclinic.clinic.patients.api.PatientSummary
import uz.medclinic.clinic.shared.Permissions
import uz.medclinic.platform.domain.ValidationException
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class LabPatient(val id: UUID, val cardNo: Long, val fullName: String, val gender: String, val birthDate: LocalDate) {
    constructor(p: PatientSummary) : this(p.id, p.cardNo, p.fullName, p.gender, p.birthDate)
}

data class WorkItemResponse(
    val id: UUID,
    val serviceName: String,
    val patient: LabPatient?,
    val status: ResultStatus,
    val createdAt: Instant,
    val readyAt: Instant?,
    val version: Int,
)

data class ResultRowResponse(
    val row: FormRow,
    val value: RowValue?,
    val flag: Flag?,
    val norm: String?,
    val normalLow: BigDecimal?,
    val normalHigh: BigDecimal?,
    val criticalLow: BigDecimal?,
    val criticalHigh: BigDecimal?,
)

data class LabResultResponse(
    val id: UUID,
    val visitId: UUID,
    val serviceName: String,
    val templateName: String?,
    val patient: LabPatient?,
    val status: ResultStatus,
    val state: PhysioState,
    val customState: String?,
    val comment: String?,
    val rows: List<ResultRowResponse>,
    val usesStates: Boolean,
    val customStates: List<String>,
    val readyByName: String?,
    val readyAt: Instant?,
    val correctedAt: Instant?,
    val correctionReason: String?,
    val createdAt: Instant,
    val version: Int,
) {
    constructor(v: ResultView) : this(
        v.result.id, v.result.visitId, v.result.serviceName, v.templateName, v.patient?.let(::LabPatient), v.result.status, v.result.state,
        v.result.customState, v.result.comment,
        v.rows.map { ResultRowResponse(it.row, it.value, it.flag, it.norm, it.normalLow, it.normalHigh, it.criticalLow, it.criticalHigh) },
        v.usesStates, v.customStates, v.result.readyByName, v.result.readyAt, v.result.correctedAt, v.result.correctionReason,
        v.result.createdAt, v.result.version,
    )
}

data class SaveResultRequest(
    val state: PhysioState? = null,
    @field:Size(max = 60) val customState: String? = null,
    @field:Size(max = 200) val values: Map<UUID, RowValue> = emptyMap(),
    @field:Size(max = 2000) val comment: String? = null,
    @field:Size(max = 500) val reason: String? = null,
    val version: Int?,
)

data class VersionRequest(val version: Int?)

@RestController
@RequestMapping("/api/v1/lab")
class LabResultController(private val results: LabResultService) {
    /** Today's work: results not ready yet (any day) and everything that arrived on [date] (default today). */
    @GetMapping("/work")
    @PreAuthorize(Permissions.HAS_LAB_WORK)
    fun work(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?) =
        results.workList(date).map { (r, p) -> WorkItemResponse(r.id, r.serviceName, p?.let(::LabPatient), r.status, r.createdAt, r.readyAt, r.version) }

    @GetMapping("/results/{id}")
    @PreAuthorize(Permissions.HAS_LAB_RESULT_READ)
    fun get(
        @PathVariable id: UUID,
        @RequestParam(required = false) state: PhysioState?,
        @RequestParam(required = false) customState: String?,
    ) = LabResultResponse(results.view(id, state, customState))

    @GetMapping("/results")
    @PreAuthorize(Permissions.HAS_LAB_RESULT_READ)
    fun byPatient(@RequestParam patientId: UUID) = results.byPatient(patientId).map(::LabResultResponse)

    @PutMapping("/results/{id}")
    @PreAuthorize(Permissions.HAS_LAB_WORK)
    fun save(@PathVariable id: UUID, @Valid @RequestBody body: SaveResultRequest) =
        LabResultResponse(results.save(id, version(body.version), body.state, body.customState, body.values, body.comment))

    @PostMapping("/results/{id}/ready")
    @PreAuthorize(Permissions.HAS_LAB_WORK)
    fun ready(@PathVariable id: UUID, @RequestBody body: VersionRequest) = LabResultResponse(results.ready(id, version(body.version)))

    @PostMapping("/results/{id}/correct")
    @PreAuthorize(Permissions.HAS_LAB_WORK)
    fun correct(@PathVariable id: UUID, @Valid @RequestBody body: SaveResultRequest) =
        LabResultResponse(results.correct(id, version(body.version), body.state, body.customState, body.values, body.comment, body.reason))

    /** Signature of the lab worker who made the result ready, for the printout. */
    @GetMapping("/results/{id}/signature")
    @PreAuthorize(Permissions.HAS_LAB_RESULT_READ)
    fun signature(@PathVariable id: UUID): ResponseEntity<ByteArray> {
        val (bytes, type) = results.signature(id)
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type))
            .cacheControl(CacheControl.maxAge(Duration.ZERO).cachePrivate().mustRevalidate()).body(bytes)
    }

    private fun version(v: Int?) = v ?: throw ValidationException("version", "validation.not_null")
}
