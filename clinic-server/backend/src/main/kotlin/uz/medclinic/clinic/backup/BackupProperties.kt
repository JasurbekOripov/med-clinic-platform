package uz.medclinic.clinic.backup

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Nightly database backup. On a clinic server the installer sets [directory] to the second disk and [command] to the
 * bundled `pg_dump` (ASSUMPTION until the M9 installer: the PostgreSQL client tools ship with the server install).
 */
@ConfigurationProperties("clinic.backup")
data class BackupProperties(
    val enabled: Boolean = true,
    val directory: String = "./data/backups",
    /** Executable of PostgreSQL's dump tool (full path on Windows). */
    val command: String = "pg_dump",
    /** Six-field Spring cron in Asia/Tashkent; default 02:30 every night, when nobody works. */
    val cron: String = "0 30 2 * * *",
    val timeout: Duration = Duration.ofHours(2),
    /** Old files beyond the retention period are deleted, but never the newest successful ones. */
    val keepAtLeast: Int = 3,
)
