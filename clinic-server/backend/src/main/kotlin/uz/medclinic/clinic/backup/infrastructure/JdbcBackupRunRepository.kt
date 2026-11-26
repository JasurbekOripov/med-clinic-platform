package uz.medclinic.clinic.backup.infrastructure

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import uz.medclinic.clinic.backup.domain.BackupRun
import uz.medclinic.clinic.backup.domain.BackupRunRepository
import uz.medclinic.clinic.backup.domain.BackupStatus
import uz.medclinic.clinic.backup.domain.BackupTrigger
import uz.medclinic.platform.persistence.instant
import uz.medclinic.platform.persistence.instantOrNull
import uz.medclinic.platform.persistence.longOrNull
import uz.medclinic.platform.persistence.toDb
import uz.medclinic.platform.persistence.toDbOrNull
import uz.medclinic.platform.persistence.uuid
import uz.medclinic.platform.persistence.uuidOrNull
import java.sql.ResultSet

private const val DEFAULT_RETENTION_DAYS = 30

@Repository
class JdbcBackupRunRepository(private val jdbc: JdbcClient) : BackupRunRepository {
    override fun insert(run: BackupRun) {
        jdbc.sql(
            "insert into backup_run (id, started_at, status, trigger_kind, triggered_by) values (:id, :startedAt, :status, :trigger, :by)",
        ).param("id", run.id).param("startedAt", run.startedAt.toDb()).param("status", run.status.name)
            .param("trigger", run.trigger.name).param("by", run.triggeredBy).update()
    }

    override fun update(run: BackupRun) {
        jdbc.sql(
            """
            update backup_run set finished_at = :finishedAt, status = :status, file_name = :file, size_bytes = :size, sha256 = :sha, error = :error
            where id = :id
            """,
        ).param("id", run.id).param("finishedAt", run.finishedAt.toDbOrNull()).param("status", run.status.name)
            .param("file", run.fileName).param("size", run.sizeBytes).param("sha", run.sha256).param("error", run.error).update()
    }

    override fun recent(limit: Int): List<BackupRun> =
        jdbc.sql("select * from backup_run order by started_at desc, id desc limit :limit").param("limit", limit).query(::map).list()

    override fun lastSuccess(): BackupRun? =
        jdbc.sql("select * from backup_run where status = 'SUCCESS' order by started_at desc limit 1").query(::map).optional().orElse(null)

    override fun running(): BackupRun? =
        jdbc.sql("select * from backup_run where status = 'RUNNING' order by started_at desc limit 1").query(::map).optional().orElse(null)

    /** The settings row is seeded by the migration; a missing row (e.g. emptied tables) means the default. */
    override fun retentionDays(): Int =
        jdbc.sql("select retention_days from backup_settings where id = 1").query(Int::class.java).optional().orElse(DEFAULT_RETENTION_DAYS)

    override fun setRetentionDays(days: Int) {
        jdbc.sql(
            """
            insert into backup_settings (id, retention_days) values (1, :d)
            on conflict (id) do update set retention_days = excluded.retention_days, version = backup_settings.version + 1
            """,
        ).param("d", days).update()
    }

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = BackupRun(
        id = rs.uuid("id"),
        startedAt = rs.instant("started_at"),
        finishedAt = rs.instantOrNull("finished_at"),
        status = BackupStatus.valueOf(rs.getString("status")),
        trigger = BackupTrigger.valueOf(rs.getString("trigger_kind")),
        triggeredBy = rs.uuidOrNull("triggered_by"),
        fileName = rs.getString("file_name"),
        sizeBytes = rs.longOrNull("size_bytes"),
        sha256 = rs.getString("sha256"),
        error = rs.getString("error"),
    )
}
