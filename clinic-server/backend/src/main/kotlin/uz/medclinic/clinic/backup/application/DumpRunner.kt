package uz.medclinic.clinic.backup.application

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

data class DbTarget(val host: String, val port: Int, val database: String, val user: String, val password: String) {
    companion object {
        /** `jdbc:postgresql://host:5432/clinic?ssl=false` → host, port, database. */
        fun fromJdbc(url: String, user: String, password: String): DbTarget {
            require(url.startsWith("jdbc:postgresql://")) { "Only PostgreSQL JDBC URLs are supported" }
            val uri = URI(url.removePrefix("jdbc:"))
            return DbTarget(uri.host ?: "localhost", if (uri.port > 0) uri.port else 5432, uri.path.trim('/'), user, password)
        }
    }
}

data class DumpResult(val file: Path, val sizeBytes: Long, val sha256: String)

class DumpFailed(message: String) : RuntimeException(message)

/**
 * Runs PostgreSQL's dump tool into [directory]. The password goes through the `PGPASSWORD` environment variable, never
 * the command line (which other users of the PC could see). Writes to a `.part` file and renames it only on success,
 * so a half-written file is never mistaken for a backup.
 */
class DumpRunner(private val command: String, private val directory: Path, private val timeout: Duration) {
    fun dump(target: DbTarget, at: Instant): DumpResult {
        Files.createDirectories(directory)
        val name = "medclinic-${FILE_TIME.format(LocalDateTime.ofInstant(at, ZONE))}.dump"
        val part = directory.resolve("$name.part")
        val file = directory.resolve(name)
        val process = ProcessBuilder(
            command, "--format=custom", "--no-owner", "--no-privileges",
            "--host", target.host, "--port", target.port.toString(), "--username", target.user, "--dbname", target.database,
            "--file", part.toString(),
        ).redirectErrorStream(true).also { it.environment()["PGPASSWORD"] = target.password }.start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            Files.deleteIfExists(part)
            throw DumpFailed("timeout after $timeout")
        }
        if (process.exitValue() != 0 || !Files.exists(part) || part.fileSize() == 0L) {
            Files.deleteIfExists(part)
            throw DumpFailed("exit ${process.exitValue()}: ${output.trim().take(800)}")
        }
        Files.move(part, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        return DumpResult(file, file.fileSize(), sha256(file))
    }

    /**
     * Deletes backup files older than [retentionDays], always keeping the newest [keepAtLeast]. Leftover `.part` files
     * of interrupted runs are removed too. Returns the deleted file names.
     */
    fun cleanup(retentionDays: Int, keepAtLeast: Int, now: Instant): List<String> {
        if (!Files.isDirectory(directory)) return emptyList()
        val files = directory.listDirectoryEntries("medclinic-*").filter { it.isRegularFile() }
        val parts = files.filter { it.name.endsWith(".part") && it.getLastModifiedTime().toInstant().isBefore(now.minus(timeout)) }
        val dumps = files.filter { it.name.endsWith(".dump") }.sortedByDescending { it.name }
        val limit = now.minus(Duration.ofDays(retentionDays.toLong()))
        val old = dumps.drop(keepAtLeast).filter { it.getLastModifiedTime().toInstant().isBefore(limit) }
        return (parts + old).map { Files.deleteIfExists(it); it.name }
    }

    fun freeSpace(): Long? = runCatching { Files.createDirectories(directory); Files.getFileStore(directory).usableSpace }.getOrNull()

    private fun sha256(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Asia/Tashkent")
        val FILE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
