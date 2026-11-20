package uz.medclinic.vendor.monitoring

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uz.medclinic.vendor.monitoring.api.AlertType
import uz.medclinic.vendor.monitoring.domain.DeviceHealth
import uz.medclinic.vendor.monitoring.domain.HealthRules
import uz.medclinic.vendor.monitoring.domain.HealthThresholds
import uz.medclinic.vendor.monitoring.domain.ServerStatus
import uz.medclinic.vendor.monitoring.domain.ServerStatusRule
import uz.medclinic.vendorprotocol.BackupStatus
import uz.medclinic.vendorprotocol.DiskUsage
import java.time.Duration
import java.time.Instant
import java.util.UUID

class HealthRulesTest {
    private val now = Instant.parse("2026-11-20T06:00:00Z")
    private val gb = 1024L * 1024 * 1024

    private fun health(
        disks: List<DiskUsage> = listOf(DiskUsage("C:", 500 * gb, 200 * gb)),
        status: BackupStatus? = null,
        lastSuccess: Instant? = null,
        skew: Long = 0,
    ) = DeviceHealth(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), now, "1.0", 1, disks, lastSuccess, status, lastSuccess,
        null, null, null, skew, null,
    )

    private fun types(h: DeviceHealth) = HealthRules.evaluate(h, HealthThresholds(), now).map { it.type }

    @Test
    fun `healthy server has no alerts, missing backup info is not an alert`() {
        assertThat(types(health())).isEmpty()
    }

    @Test
    fun `disk is low by ratio or by absolute free space`() {
        assertThat(types(health(disks = listOf(DiskUsage("C:", 500 * gb, 40 * gb))))).containsExactly(AlertType.DISK_LOW)
        assertThat(types(health(disks = listOf(DiskUsage("D:", 20 * gb, 4 * gb))))).containsExactly(AlertType.DISK_LOW)
        assertThat(types(health(disks = listOf(DiskUsage("E:", 0, 0))))).isEmpty()
    }

    @Test
    fun `backup failed, stale backup and clock skew`() {
        assertThat(types(health(status = BackupStatus.FAILED, lastSuccess = now.minus(Duration.ofHours(37)))))
            .containsExactlyInAnyOrder(AlertType.BACKUP_FAILED, AlertType.BACKUP_STALE)
        assertThat(types(health(status = BackupStatus.SUCCESS, lastSuccess = now.minus(Duration.ofHours(35))))).isEmpty()
        assertThat(types(health(skew = -301))).containsExactly(AlertType.CLOCK_SKEW)
        assertThat(types(health(skew = 300))).isEmpty()
    }

    @Test
    fun `server status`() {
        val offlineAfter = Duration.ofMinutes(10)
        assertThat(ServerStatusRule.of(false, null, offlineAfter, now)).isEqualTo(ServerStatus.NOT_PAIRED)
        assertThat(ServerStatusRule.of(true, null, offlineAfter, now)).isEqualTo(ServerStatus.NEVER_SEEN)
        assertThat(ServerStatusRule.of(true, now.minusSeconds(600), offlineAfter, now)).isEqualTo(ServerStatus.ONLINE)
        assertThat(ServerStatusRule.of(true, now.minusSeconds(601), offlineAfter, now)).isEqualTo(ServerStatus.OFFLINE)
    }
}
