package uz.medclinic.clinic.backup.domain

import java.time.Instant
import java.util.UUID

enum class BackupStatus { RUNNING, SUCCESS, FAILED }

enum class BackupTrigger { SCHEDULED, MANUAL }

data class BackupRun(
    val id: UUID,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val status: BackupStatus,
    val trigger: BackupTrigger,
    val triggeredBy: UUID?,
    val fileName: String?,
    val sizeBytes: Long?,
    val sha256: String?,
    val error: String?,
)

interface BackupRunRepository {
    fun insert(run: BackupRun)
    fun update(run: BackupRun)
    fun recent(limit: Int): List<BackupRun>
    fun lastSuccess(): BackupRun?
    fun running(): BackupRun?
    fun retentionDays(): Int
    fun setRetentionDays(days: Int)
}
