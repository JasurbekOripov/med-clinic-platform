package uz.medclinic.clinic.patients.web

import jakarta.validation.Valid
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uz.medclinic.clinic.patients.application.PatientService
import uz.medclinic.clinic.patients.application.PossibleDuplicateException
import uz.medclinic.clinic.patients.domain.DuplicateReason
import uz.medclinic.clinic.patients.domain.Gender
import uz.medclinic.clinic.patients.domain.Patient
import uz.medclinic.clinic.patients.domain.PatientDetails
import uz.medclinic.clinic.shared.Permissions
import uz.medclinic.platform.domain.PageQuery
import uz.medclinic.platform.domain.ValidationException
import uz.medclinic.platform.web.PageResponse
import uz.medclinic.platform.web.ProblemDetails
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PatientResponse(
    val id: UUID,
    val cardNo: Long,
    val lastName: String,
    val firstName: String,
    val patronymic: String?,
    val fullName: String,
    val gender: Gender,
    val birthDate: LocalDate,
    val phone: String,
    val address: String?,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Int,
) {
    constructor(p: Patient) : this(
        p.id, p.cardNo, p.details.lastName, p.details.firstName, p.details.patronymic, p.details.fullName, p.details.gender,
        p.details.birthDate, p.details.phone.e164, p.details.address, p.details.notes, p.createdAt, p.updatedAt, p.version,
    )
}

data class DuplicateResponse(val patient: PatientResponse, val reason: DuplicateReason)

data class PatientRequest(
    @field:Size(max = 100) val lastName: String?,
    @field:Size(max = 100) val firstName: String?,
    @field:Size(max = 100) val patronymic: String?,
    val gender: Gender?,
    val birthDate: LocalDate?,
    @field:Size(max = 32) val phone: String?,
    @field:Size(max = 500) val address: String?,
    @field:Size(max = 2000) val notes: String?,
    /** Set after the receptionist saw the duplicate warning and confirmed it is a different person. */
    val confirmedNotDuplicate: Boolean = false,
    @field:PositiveOrZero val version: Int? = null,
)

@RestController
@RequestMapping("/api/v1/patients")
class PatientController(private val patients: PatientService) {
    @GetMapping
    @PreAuthorize(Permissions.HAS_PATIENT_VIEW)
    fun search(@RequestParam(required = false) q: String?, @RequestParam(required = false) page: Int?, @RequestParam(required = false) size: Int?) =
        PageResponse.of(patients.search(q, PageQuery.of(page, size)), ::PatientResponse)

    @GetMapping("/{id}")
    @PreAuthorize(Permissions.HAS_PATIENT_VIEW)
    fun get(@PathVariable id: UUID) = PatientResponse(patients.get(id))

    /** Live check while typing, so the warning appears before the form is submitted. */
    @PostMapping("/duplicates")
    @PreAuthorize(Permissions.HAS_PATIENT_EDIT)
    fun duplicates(@RequestBody body: PatientRequest, @RequestParam(required = false) excludeId: UUID?): List<DuplicateResponse> {
        val details = runCatching { details(body) }.getOrNull() ?: return emptyList()
        return patients.duplicates(details, excludeId).map { DuplicateResponse(PatientResponse(it.patient), it.reason) }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.HAS_PATIENT_EDIT)
    fun create(@Valid @RequestBody body: PatientRequest) = PatientResponse(patients.create(details(body), body.confirmedNotDuplicate))

    @PutMapping("/{id}")
    @PreAuthorize(Permissions.HAS_PATIENT_EDIT)
    fun update(@PathVariable id: UUID, @Valid @RequestBody body: PatientRequest) =
        PatientResponse(patients.update(id, body.version ?: throw ValidationException("version", "validation.not_null"), details(body)))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Permissions.HAS_PATIENT_DELETE)
    fun delete(@PathVariable id: UUID, @RequestParam version: Int) = patients.delete(id, version)

    /** 409 with the candidates, so the UI can offer "open existing patient". */
    @ExceptionHandler(PossibleDuplicateException::class)
    fun duplicate(ex: PossibleDuplicateException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetails.of(HttpStatus.CONFLICT, ex.code)
        problem.setProperty("candidates", ex.candidates.map { DuplicateResponse(PatientResponse(it.patient), it.reason) })
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem)
    }

    private fun details(b: PatientRequest) =
        PatientDetails.of(b.lastName, b.firstName, b.patronymic, b.gender, b.birthDate, b.phone, b.address, b.notes, patients.today())
}
