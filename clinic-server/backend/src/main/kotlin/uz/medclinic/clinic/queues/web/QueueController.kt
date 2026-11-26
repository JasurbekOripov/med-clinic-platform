package uz.medclinic.clinic.queues.web

import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.format.annotation.DateTimeFormat
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
import uz.medclinic.clinic.identity.api.StaffDirectory
import uz.medclinic.clinic.patients.api.PatientQueries
import uz.medclinic.clinic.queues.application.QueueService
import uz.medclinic.clinic.queues.application.TicketService
import uz.medclinic.clinic.queues.domain.Queue
import uz.medclinic.clinic.queues.domain.QueueDetails
import uz.medclinic.clinic.queues.domain.Ticket
import uz.medclinic.clinic.queues.domain.TicketStatus
import uz.medclinic.clinic.shared.Permissions
import uz.medclinic.platform.domain.ValidationException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class QueueResponse(
    val id: UUID,
    val name: String,
    val prefix: String,
    val room: String?,
    val doctorId: UUID?,
    val doctorName: String?,
    val serviceIds: List<UUID>,
    val active: Boolean,
    val version: Int,
)

data class QueueRequest(
    @field:Size(max = 100) val name: String?,
    @field:Size(max = 2) val prefix: String?,
    @field:Size(max = 50) val room: String?,
    val doctorId: UUID? = null,
    @field:Size(max = 500) val serviceIds: Set<UUID> = emptySet(),
    val active: Boolean = true,
    val version: Int? = null,
)

data class TicketPatient(val id: UUID, val cardNo: Long, val fullName: String)

data class TicketLine(val serviceName: String, val doctorName: String?)

data class TicketResponse(
    val id: UUID,
    val label: String,
    val number: Int,
    val status: TicketStatus,
    val patient: TicketPatient?,
    val lines: List<TicketLine>,
    val createdAt: Instant,
    val doneAt: Instant?,
    val version: Int,
)

data class TicketDoneRequest(val done: Boolean = true, val version: Int?)

@RestController
@RequestMapping("/api/v1/queues")
class QueueController(
    private val queues: QueueService,
    private val tickets: TicketService,
    private val staff: StaffDirectory,
    private val patients: PatientQueries,
) {
    @GetMapping
    @PreAuthorize(Permissions.HAS_QUEUE_READ)
    fun list() = queues.list().map(::response)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.HAS_SETTINGS_MANAGE)
    fun create(@Valid @RequestBody body: QueueRequest) = response(queues.create(details(body)))

    @PutMapping("/{id}")
    @PreAuthorize(Permissions.HAS_SETTINGS_MANAGE)
    fun update(@PathVariable id: UUID, @Valid @RequestBody body: QueueRequest) = response(queues.update(id, version(body.version), details(body)))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Permissions.HAS_SETTINGS_MANAGE)
    fun delete(@PathVariable id: UUID, @RequestParam version: Int) = queues.delete(id, version)

    /**
     * Tickets of one queue for a day (default: today in Tashkent), in number order. Tickets exist only for paid
     * services, so this list never shows unpaid patients.
     */
    @GetMapping("/{id}/tickets")
    @PreAuthorize(Permissions.HAS_QUEUE_READ)
    fun tickets(
        @PathVariable id: UUID,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
    ): List<TicketResponse> {
        val (prefix, list) = tickets.today(id, date)
        val people = patients.findAll(list.map { it.patientId })
        return list.map { t -> ticket(prefix, t, people[t.patientId]?.let { TicketPatient(it.id, it.cardNo, it.fullName) }) }
    }

    /** "Seen" (or back to waiting when pressed by mistake). */
    @PostMapping("/tickets/{id}/done")
    @PreAuthorize(Permissions.HAS_QUEUE_READ)
    fun done(@PathVariable id: UUID, @RequestBody body: TicketDoneRequest) {
        tickets.setDone(id, version(body.version), body.done)
    }

    private fun ticket(prefix: String, t: Ticket, patient: TicketPatient?) = TicketResponse(
        t.id, Ticket.label(prefix, t.number), t.number, t.status, patient, t.items.map { TicketLine(it.serviceName, it.doctorName) }, t.createdAt, t.doneAt, t.version,
    )

    private fun response(q: Queue) = QueueResponse(
        q.id, q.details.name, q.details.prefix, q.details.room, q.details.doctorId, q.details.doctorId?.let { staff.find(it)?.fullName },
        q.details.serviceIds.sorted(), q.details.active, q.version,
    )

    private fun version(v: Int?) = v ?: throw ValidationException("version", "validation.not_null")

    private fun details(b: QueueRequest) = QueueDetails.of(b.name, b.prefix, b.room, b.doctorId, b.serviceIds, b.active)
}
