package uz.medclinic.clinic.backup

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import uz.medclinic.clinic.backup.application.DbTarget
import uz.medclinic.clinic.backup.application.DumpFailed
import uz.medclinic.clinic.backup.application.DumpRunner
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.time.Instant
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

class DumpRunnerTest {
    private val fake = Path.of("src/test/resources/backup/fake-pg-dump.sh").toAbsolutePath().toString()
    private val target = DbTarget("db", 5432, "clinic", "medclinic", "secret")
    private val now = Instant.parse("2026-11-26T21:30:00Z") // 02:30 in Tashkent

    @Test
    fun `parses the jdbc url`() {
        assertThat(DbTarget.fromJdbc("jdbc:postgresql://10.0.0.5:5433/clinic?ssl=false", "u", "p"))
            .isEqualTo(DbTarget("10.0.0.5", 5433, "clinic", "u", "p"))
        assertThat(DbTarget.fromJdbc("jdbc:postgresql://localhost/clinic", "u", "p").port).isEqualTo(5432)
    }

    @Test
    fun `writes a named dump with a checksum, never leaves a part file`(@TempDir dir: Path) {
        val result = DumpRunner(fake, dir, Duration.ofSeconds(30)).dump(target, now)
        assertThat(result.file.name).isEqualTo("medclinic-20261127-023000.dump")
        assertThat(result.file.readText()).contains("--format=custom").contains("--username medclinic").doesNotContain("secret")
        assertThat(result.sha256).hasSize(64)
        assertThat(dir.listDirectoryEntries().map { it.name }).containsExactly("medclinic-20261127-023000.dump")
    }

    @Test
    fun `a failing tool reports its output and leaves nothing behind`(@TempDir dir: Path) {
        val noPassword = target.copy(password = "")
        assertThatThrownBy { DumpRunner(fake, dir, Duration.ofSeconds(30)).dump(noPassword, now) }
            .isInstanceOf(DumpFailed::class.java).hasMessageContaining("no password")
        assertThat(dir.listDirectoryEntries()).isEmpty()
    }

    @Test
    fun `retention deletes old files but keeps the newest ones`(@TempDir dir: Path) {
        val runner = DumpRunner(fake, dir, Duration.ofHours(1))
        val names = (1..6).map { "medclinic-2026110${it}-023000.dump" }
        names.forEachIndexed { i, n ->
            val f = Files.writeString(dir.resolve(n), "x")
            Files.setLastModifiedTime(f, FileTime.from(now.minus(Duration.ofDays(60L - i))))
        }
        Files.writeString(dir.resolve("other.txt"), "not ours")
        val deleted = runner.cleanup(retentionDays = 30, keepAtLeast = 3, now = now)
        assertThat(deleted).containsExactlyInAnyOrder(names[0], names[1], names[2])
        assertThat(dir.resolve("other.txt").exists()).isTrue()
        assertThat(runner.cleanup(retentionDays = 30, keepAtLeast = 3, now = now)).isEmpty()
    }
}
