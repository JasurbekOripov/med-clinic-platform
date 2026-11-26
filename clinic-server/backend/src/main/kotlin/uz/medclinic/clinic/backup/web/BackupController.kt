package uz.medclinic.clinic.backup.web

import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uz.medclinic.clinic.backup.application.BackupService
import uz.medclinic.clinic.backup.domain.BackupRun
import uz.medclinic.clinic.backup.domain.BackupStatus
import uz.medclinic.clinic.backup.domain.BackupTrigger
import uz.medclinic.clinic.shared.Permissions
import java.time.Instant
import java.util.UUID

data class BackupRunResponse(
    val id: UUID,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val status: BackupStatus,
    val trigger: BackupTrigger,
    val fileName: String?,
    val sizeBytes: Long?,
    val sha256: String?,
    val error: String?,
) {
    constructor(r: BackupRun) : this(r.id, r.startedAt, r.finishedAt, r.status, r.trigger, r.fileName, r.sizeBytes, r.sha256, r.error)
}

data class BackupStatusResponse(
    val enabled: Boolean,
    val directory: String,
    val retentionDays: Int,
    val freeSpaceBytes: Long?,
    val lastSuccess: BackupRunResponse?,
    val stale: Boolean,
    val running: Boolean,
    val runs: List<BackupRunResponse>,
)

data class BackupHealthResponse(val stale: Boolean)

data class BackupSettingsRequest(val retentionDays: Int?)

data class BackupStartedResponse(val id: UUID)

/** Admin only: the clinic's data protection is a settings matter. */
@RestController
@RequestMapping("/api/v1/backups")
class BackupController(private val backups: BackupService) {
    @GetMapping
    @PreAuthorize(Permissions.HAS_SETTINGS_MANAGE)
    fun status(): BackupStatusResponse {
        val s = backups.status()
        return BackupStatusResponse(
            s.enabled, s.directory, s.retentionDays, s.freeSpaceBytes, s.lastSuccess?.let(::BackupRunResponse), s.stale, s.running,
            s.runs.map(::BackupRunResponse),
        )
    }

    /** For the warning banner shown to the Admin. */
    @GetMapping("/health")
    @PreAuthorize(Permissions.HAS_SETTINGS_MANAGE)
    fun health() = BackupHealthResponse(backups.isStale())

    @PostMapping("/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize(Permissions.HAS_SETTINGS_MANAGE)
    fun run() = BackupStartedResponse(backups.startManual())

    @PutMapping("/settings")
    @PreAuthorize(Permissions.HAS_SETTINGS_MANAGE)
    fun settings(@RequestBody body: BackupSettingsRequest): BackupStatusResponse {
        backups.setRetentionDays(body.retentionDays)
        return status()
    }
}
