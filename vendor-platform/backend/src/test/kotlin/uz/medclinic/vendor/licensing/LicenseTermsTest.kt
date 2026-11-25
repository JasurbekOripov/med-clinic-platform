package uz.medclinic.vendor.licensing

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import uz.medclinic.vendor.licensing.domain.LicenseTerms
import uz.medclinic.platform.domain.ValidationException
import java.time.Instant
import java.time.LocalDate

class LicenseTermsTest {
    @Test
    fun `normalises plan and modules and converts Tashkent days to instants`() {
        val terms = LicenseTerms.of(" standard ", listOf("lab", "REGISTRATION", "LAB"), 10, 1,
            LocalDate.of(2026, 12, 1), LocalDate.of(2027, 11, 30), null)

        assertThat(terms.plan).isEqualTo("STANDARD")
        assertThat(terms.modules).containsExactly("REGISTRATION", "LAB")
        assertThat(terms.expiryGraceDays).isEqualTo(LicenseTerms.DEFAULT_GRACE_DAYS)
        assertThat(terms.validFrom).isEqualTo(Instant.parse("2026-11-30T19:00:00Z"))
        assertThat(terms.validUntil).isEqualTo(Instant.parse("2027-11-30T19:00:00Z"))
    }

    @Test
    fun `reports every invalid field`() {
        assertThatThrownBy {
            LicenseTerms.of("bad plan!", listOf("TELEPORT"), 0, 0, LocalDate.of(2027, 1, 2), LocalDate.of(2027, 1, 1), 91)
        }.isInstanceOfSatisfying(ValidationException::class.java) { ex ->
            assertThat(ex.violations.map { it.field })
                .containsExactlyInAnyOrder("plan", "modules", "maxUsers", "maxBranches", "endsOn", "expiryGraceDays")
        }
    }
}
