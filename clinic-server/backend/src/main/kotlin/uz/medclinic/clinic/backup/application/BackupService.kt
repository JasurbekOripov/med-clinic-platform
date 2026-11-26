package uz.medclinic.clinic.backup.application

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.clinic.audit.api.AuditEvent
import uz.medclinic.clinic.audit.api.AuditRecorder
import uz.medclinic.clinic.backup.BackupProperties
import uz.medclinic.clinic.backup.domain.BackupRun
import uz.medclinic.clinic.backup.domain.BackupRunRepository
import uz.medclinic.clinic.backup.domain.BackupStatus
import uz.medclinic.clinic.backup.domain.BackupTrigger
import uz.medclinic.common.Ids
import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.domain.ValidationException
import uz.medclinic.platform.security.CurrentActor
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

data class BackupStatusView(
    val enabled: Boolean,
    val directory: String,
    val retentionDays: Int,
    val freeSpaceBytes: Long?,
    val lastSuccess: BackupRun?,
    /** No successful backup in the last [STALE_AFTER]: the Admin sees a warning. */
    val stale: Boolean,
    val running: Boolean,
    val runs: List<BackupRun>,
)

@Service
class BackupService(
    private val properties: BackupProperties,
    private val runs: BackupRunRepository,
    private val audit: AuditRecorder,
    private val clock: Clock,
    @Value("\${spring.datasource.url}") private val dbUrl: String,
    @Value("\${spring.datasource.username}") private val dbUser: String,
    @Value("\${spring.datasource.password}") private val dbPassword: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val busy = AtomicBoolean(false)
    private val runner = DumpRunner(properties.command, Path.of(properties.directory).toAbsolutePath(), properties.timeout)

    @Scheduled(cron = "\${clinic.backup.cron:0 30 2 * * *}", zone = "Asia/Tashkent")
    fun nightly() {
        if (properties.enabled) run(BackupTrigger.SCHEDULED, null, Ids.newId())
    }

    /** Starts a backup in the background (Admin's "back up now"); returns at once. */
    @Transactional
    fun startManual(): UUID {
        val by = CurrentActor.staffId()
        if (busy.get()) throw DomainException("backup.already_running", DomainException.Kind.CONFLICT)
        val id = Ids.newId()
        audit.record(AuditEvent("backup.requested", "backup", id))
        Thread.ofVirtual().name("backup-manual").start { run(BackupTrigger.MANUAL, by, id) }
        return id
    }

    /** One backup at a time; the outcome is stored in `backup_run` (and the server log), never thrown to the caller. */
    fun run(trigger: BackupTrigger, by: UUID?, id: UUID): BackupRun? {
        if (!busy.compareAndSet(false, true)) return null
        try {
            val started = BackupRun(id, clock.instant(), null, BackupStatus.RUNNING, trigger, by, null, null, null, null)
            runs.insert(started)
            val finished = try {
                val result = runner.dump(DbTarget.fromJdbc(dbUrl, dbUser, dbPassword), started.startedAt)
                runner.cleanup(runs.retentionDays(), properties.keepAtLeast, clock.instant()).forEach { log.info("Old backup removed: {}", it) }
                started.copy(finishedAt = clock.instant(), status = BackupStatus.SUCCESS, fileName = result.file.fileName.toString(), sizeBytes = result.sizeBytes, sha256 = result.sha256)
            } catch (e: Exception) {
                log.error("Backup failed: {}", e.message)
                started.copy(finishedAt = clock.instant(), status = BackupStatus.FAILED, error = (e.message ?: e.javaClass.simpleName).take(1000))
            }
            runs.update(finished)
            return finished
        } finally {
            busy.set(false)
        }
    }

    fun status(): BackupStatusView {
        val last = runs.lastSuccess()
        val now = clock.instant()
        return BackupStatusView(
            properties.enabled, Path.of(properties.directory).toAbsolutePath().toString(), runs.retentionDays(), runner.freeSpace(),
            last, last == null || last.startedAt.isBefore(now.minus(STALE_AFTER)), busy.get(), runs.recent(30),
        )
    }

    /**
     * Light check for the warning banner. (No default arguments in this class: it is a Spring proxy, and Kotlin's
     * default-argument bridge would run against the proxy's empty fields.)
     */
    fun isStale(): Boolean = runs.lastSuccess()?.startedAt?.isBefore(clock.instant().minus(STALE_AFTER)) ?: true

    @Transactional
    fun setRetentionDays(days: Int?) {
        if (days == null || days !in 3..365) throw ValidationException("retentionDays", "validation.range", mapOf("min" to 3, "max" to 365))
        val before = runs.retentionDays()
        runs.setRetentionDays(days)
        audit.record(AuditEvent("backup.settings_updated", "backup", null, before = mapOf("retentionDays" to before), after = mapOf("retentionDays" to days)))
    }

    companion object {
        val STALE_AFTER: Duration = Duration.ofHours(36)
    }
}
