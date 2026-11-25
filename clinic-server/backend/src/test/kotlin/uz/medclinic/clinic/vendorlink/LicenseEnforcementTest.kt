package uz.medclinic.clinic.vendorlink

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import uz.medclinic.clinic.support.IntegrationTest
import uz.medclinic.clinic.support.TestLicenses
import java.time.Duration
import java.time.Instant

class LicenseEnforcementTest : IntegrationTest() {
    @Test
    fun `without a license nothing works except sign-in, setup and the license page`() {
        pairClinic(license = null)
        val admin = createStaff()

        mockMvc.get("/api/v1/staff") { with(asStaff(admin)) }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("license.none") }
        }
        mockMvc.get("/api/v1/license/status").andExpect { jsonPath("$.state") { value("NONE") } }
        mockMvc.get("/api/v1/auth/me") { with(asStaff(admin)) }.andExpect { status { isOk() } }
    }

    @Test
    fun `grace period warns but works, after grace the program stops`() {
        pairClinic(TestLicenses.payload(clinicId, validUntil = Instant.parse("2026-11-20T00:00:00Z"), graceDays = 14))
        val admin = createStaff()

        mockMvc.get("/api/v1/license/status").andExpect {
            jsonPath("$.state") { value("GRACE") }
            jsonPath("$.usable") { value(true) }
            jsonPath("$.graceEndsAt") { value("2026-12-04T00:00:00Z") }
        }
        mockMvc.get("/api/v1/staff") { with(asStaff(admin)) }.andExpect { status { isOk() } }

        clock.advance(Duration.ofDays(10))
        mockMvc.get("/api/v1/staff") { with(asStaff(admin)) }.andExpect { jsonPath("$.code") { value("license.expired") } }
    }

    @Test
    fun `moving the clock back blocks work until the time is right again`() {
        pairClinic()
        val admin = createStaff()
        installationRepository.observeTime(clock.instant())

        clock.advance(Duration.ofHours(-2))
        mockMvc.get("/api/v1/staff") { with(asStaff(admin)) }.andExpect { jsonPath("$.code") { value("license.clock_rollback") } }

        clock.advance(Duration.ofHours(2))
        mockMvc.get("/api/v1/staff") { with(asStaff(admin)) }.andExpect { status { isOk() } }
    }

    @Test
    fun `admin can upload a license file, forged or older files are rejected`() {
        pairClinic(TestLicenses.payload(clinicId, serial = 5, validUntil = Instant.parse("2026-11-01T00:00:00Z"), graceDays = 0))
        val admin = createStaff()
        fun upload(bytes: ByteArray) = mockMvc.multipart("/api/v1/license/upload") {
            file(MockMultipartFile("file", "x.lic", "application/json", bytes)); with(asStaff(admin))
        }

        upload(TestLicenses.file(TestLicenses.payload(clinicId, serial = 6), TestLicenses.untrustedSigner))
            .andExpect { jsonPath("$.fieldErrors[0].code") { value("validation.license_bad_signature") } }
        upload(TestLicenses.file(TestLicenses.payload(java.util.UUID.randomUUID(), serial = 6)))
            .andExpect { jsonPath("$.fieldErrors[0].code") { value("validation.license_wrong_clinic") } }
        upload(TestLicenses.file(TestLicenses.payload(clinicId, serial = 4)))
            .andExpect { jsonPath("$.fieldErrors[0].code") { value("validation.license_older") } }
        upload(TestLicenses.file(TestLicenses.payload(clinicId, serial = 6, validUntil = Instant.parse("2028-01-01T00:00:00Z"))))
            .andExpect { jsonPath("$.state") { value("ACTIVE") } }
        assertThat(jdbc.sql("select count(*) from audit_log where action = 'license.installed'").query(Long::class.java).single()).isEqualTo(2)
    }
}
