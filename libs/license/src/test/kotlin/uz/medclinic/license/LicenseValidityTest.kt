package uz.medclinic.license

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class LicenseValidityTest {
    private val payload = LicenseFixtures.payload(
        validFrom = Instant.parse("2026-01-01T00:00:00Z"),
        validUntil = Instant.parse("2027-01-01T00:00:00Z"),
        graceDays = 14,
    )

    @Test
    fun `status follows validity window and grace period with exclusive ends`() {
        fun at(s: String) = LicenseValidity.evaluate(payload, Instant.parse(s)).status

        assertThat(at("2025-12-31T23:59:59Z")).isEqualTo(LicenseTimeStatus.PENDING)
        assertThat(at("2026-01-01T00:00:00Z")).isEqualTo(LicenseTimeStatus.ACTIVE)
        assertThat(at("2026-12-31T23:59:59Z")).isEqualTo(LicenseTimeStatus.ACTIVE)
        assertThat(at("2027-01-01T00:00:00Z")).isEqualTo(LicenseTimeStatus.GRACE)
        assertThat(at("2027-01-14T23:59:59Z")).isEqualTo(LicenseTimeStatus.GRACE)
        assertThat(at("2027-01-15T00:00:00Z")).isEqualTo(LicenseTimeStatus.EXPIRED)
    }

    @Test
    fun `grace is usable, pending and expired are not`() {
        assertThat(LicenseValidity.evaluate(payload, Instant.parse("2027-01-05T00:00:00Z")).usable).isTrue()
        assertThat(LicenseValidity.evaluate(payload, Instant.parse("2025-01-05T00:00:00Z")).usable).isFalse()
        assertThat(LicenseValidity.evaluate(payload, Instant.parse("2028-01-05T00:00:00Z")).usable).isFalse()
    }

    @Test
    fun `payload invariants are enforced`() {
        assertThatThrownBy { LicenseFixtures.payload(validUntil = Instant.parse("2025-01-01T00:00:00Z")) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { LicenseFixtures.payload(graceDays = 91) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { LicenseFixtures.payload(modules = listOf("LAB", "LAB")) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
