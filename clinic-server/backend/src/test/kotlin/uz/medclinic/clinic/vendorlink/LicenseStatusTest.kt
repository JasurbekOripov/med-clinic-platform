package uz.medclinic.clinic.vendorlink

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uz.medclinic.clinic.support.TestLicenses
import uz.medclinic.clinic.vendorlink.domain.ClockGuard
import uz.medclinic.clinic.vendorlink.domain.LicenseState
import uz.medclinic.clinic.vendorlink.domain.LicenseStatus
import java.time.Duration
import java.time.Instant
import java.util.UUID

class LicenseStatusTest {
    private val payload = TestLicenses.payload(UUID.randomUUID(), validUntil = Instant.parse("2027-01-01T00:00:00Z"), graceDays = 14)

    private fun at(now: String, revoked: Boolean = false, rolledBack: Boolean = false, invalid: Boolean = false, payload: Any? = this.payload) =
        LicenseStatus.evaluate(payload as uz.medclinic.license.LicensePayload?, invalid, revoked, rolledBack, false, Instant.parse(now)).state

    @Test
    fun `decision table`() {
        assertThat(at("2026-06-01T00:00:00Z")).isEqualTo(LicenseState.ACTIVE)
        assertThat(at("2025-06-01T00:00:00Z")).isEqualTo(LicenseState.PENDING)
        assertThat(at("2027-01-10T00:00:00Z")).isEqualTo(LicenseState.GRACE)
        assertThat(at("2027-01-15T00:00:00Z")).isEqualTo(LicenseState.EXPIRED)
        assertThat(at("2026-06-01T00:00:00Z", revoked = true)).isEqualTo(LicenseState.REVOKED)
        assertThat(at("2026-06-01T00:00:00Z", payload = null)).isEqualTo(LicenseState.NONE)
        assertThat(at("2026-06-01T00:00:00Z", invalid = true)).isEqualTo(LicenseState.INVALID)
        assertThat(at("2026-06-01T00:00:00Z", revoked = true, rolledBack = true)).isEqualTo(LicenseState.CLOCK_ROLLBACK)
        assertThat(LicenseState.GRACE.usable).isTrue()
        assertThat(LicenseState.EXPIRED.usable).isFalse()
    }

    @Test
    fun `clock guard tolerates small corrections only`() {
        val maxSeen = Instant.parse("2026-11-25T06:00:00Z")
        val tolerance = Duration.ofMinutes(10)
        assertThat(ClockGuard.rolledBack(maxSeen.minus(Duration.ofMinutes(9)), maxSeen, tolerance)).isFalse()
        assertThat(ClockGuard.rolledBack(maxSeen.minus(Duration.ofMinutes(11)), maxSeen, tolerance)).isTrue()
        assertThat(ClockGuard.rolledBack(maxSeen, null, tolerance)).isFalse()
    }
}
