package uz.medclinic.vendor.licensing

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import uz.medclinic.license.Ed25519Keys
import uz.medclinic.license.LicenseVerification
import uz.medclinic.license.LicenseVerifier
import uz.medclinic.vendor.identity.domain.StaffAccount
import uz.medclinic.vendor.identity.domain.VendorRole
import uz.medclinic.vendor.support.IntegrationTest
import java.time.Instant
import java.util.UUID

class LicenseApiTest : IntegrationTest() {
    private val terms = """"plan":"standard","modules":["REGISTRATION","CASHIER","LAB"],"maxUsers":25,"maxBranches":1,
        "startsOn":"2026-11-20","endsOn":"2027-11-19","expiryGraceDays":14"""

    private fun clinic(staff: StaffAccount): UUID {
        val response = mockMvc.post("/api/v1/clinics") {
            with(asStaff(staff)); contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Shifo Med","ownerName":"Vali Aliyev","phone":"901234567","region":"NAVOI"}"""
        }.andReturn().response.contentAsString
        return UUID.fromString(body(response).path("id").asString())
    }

    private fun issue(staff: StaffAccount, clinicId: UUID) = mockMvc.post("/api/v1/clinics/$clinicId/licenses") {
        with(asStaff(staff)); contentType = MediaType.APPLICATION_JSON; content = "{$terms}"
    }

    private fun idOf(json: String) = UUID.fromString(body(json).path("id").asString())

    @Test
    fun `issued license file verifies offline with the published public key`() {
        val owner = createStaff("owner")
        val clinicId = clinic(owner)

        val licenseId = idOf(
            issue(owner, clinicId).andExpect {
                status { isCreated() }
                jsonPath("$.serial") { value(1) }
                jsonPath("$.plan") { value("STANDARD") }
                jsonPath("$.status") { value("ACTIVE") }
                jsonPath("$.validFrom") { value("2026-11-19T19:00:00Z") }
                jsonPath("$.graceEndsAt") { value("2027-12-03T19:00:00Z") }
            }.andReturn().response.contentAsString,
        )

        val keys = body(mockMvc.get("/api/v1/license-keys") { with(asStaff(owner)) }.andReturn().response.contentAsString)
        val publicKey = Ed25519Keys.publicKeyFromBase64(keys.path("vk-test-1").asString())
        val file = mockMvc.get("/api/v1/licenses/$licenseId/file") { with(asStaff(owner)) }.andExpect {
            status { isOk() }
            header { string("Content-Disposition", "attachment; filename=\"license-CL-0001-1.lic\"") }
        }.andReturn().response.contentAsByteArray

        val result = LicenseVerifier(mapOf("vk-test-1" to publicKey)).verify(file, expectedClinicId = clinicId)
        assertThat(result).isInstanceOf(LicenseVerification.Valid::class.java)
        val payload = (result as LicenseVerification.Valid).payload
        assertThat(payload.licenseId).isEqualTo(licenseId)
        assertThat(payload.clinicCode).isEqualTo("CL-0001")
        assertThat(payload.modules).containsExactly("REGISTRATION", "CASHIER", "LAB")
        assertThat(payload.validUntil).isEqualTo(Instant.parse("2027-11-19T19:00:00Z"))
    }

    @Test
    fun `only one current license per clinic, renewal supersedes it`() {
        val owner = createStaff("owner")
        val clinicId = clinic(owner)
        val first = idOf(issue(owner, clinicId).andReturn().response.contentAsString)

        issue(owner, clinicId).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("license.current_exists") }
        }

        val renewed = idOf(
            mockMvc.post("/api/v1/licenses/$first/renew") {
                with(asStaff(owner)); contentType = MediaType.APPLICATION_JSON
                content = """{$terms,"maxUsers":50,"version":0}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.serial") { value(2) }
                jsonPath("$.renewedFrom") { value(first.toString()) }
            }.andReturn().response.contentAsString,
        )

        mockMvc.get("/api/v1/clinics/$clinicId/licenses") { with(asStaff(owner)) }.andExpect {
            jsonPath("$[0].id") { value(renewed.toString()) }
            jsonPath("$[0].status") { value("ACTIVE") }
            jsonPath("$[1].status") { value("SUPERSEDED") }
            jsonPath("$[1].supersededBy") { value(renewed.toString()) }
        }
        mockMvc.post("/api/v1/licenses/$first/renew") {
            with(asStaff(owner)); contentType = MediaType.APPLICATION_JSON; content = """{$terms,"version":1}"""
        }.andExpect { jsonPath("$.code") { value("license.not_current") } }
    }

    @Test
    fun `revocation requires a reason and frees the clinic for a new license`() {
        val owner = createStaff("owner")
        val clinicId = clinic(owner)
        val id = idOf(issue(owner, clinicId).andReturn().response.contentAsString)

        mockMvc.post("/api/v1/licenses/$id/revoke") {
            with(asStaff(owner)); contentType = MediaType.APPLICATION_JSON; content = """{"reason":" ","version":0}"""
        }.andExpect { status { isBadRequest() } }
        mockMvc.post("/api/v1/licenses/$id/revoke") {
            with(asStaff(owner)); contentType = MediaType.APPLICATION_JSON; content = """{"reason":"Contract ended","version":0}"""
        }.andExpect {
            jsonPath("$.status") { value("REVOKED") }
            jsonPath("$.revokeReason") { value("Contract ended") }
        }

        issue(owner, clinicId).andExpect { status { isCreated() } }
    }

    @Test
    fun `status follows the clock including grace`() {
        val owner = createStaff("owner")
        val clinicId = clinic(owner)
        val id = idOf(issue(owner, clinicId).andReturn().response.contentAsString)

        clock.set(Instant.parse("2027-11-25T00:00:00Z"))
        mockMvc.get("/api/v1/licenses/$id") { with(asStaff(owner)) }.andExpect { jsonPath("$.status") { value("GRACE") } }
        clock.set(Instant.parse("2027-12-04T00:00:00Z"))
        mockMvc.get("/api/v1/licenses/$id") { with(asStaff(owner)) }.andExpect { jsonPath("$.status") { value("EXPIRED") } }
    }

    @Test
    fun `support can view and download but not issue`() {
        val owner = createStaff("owner")
        val support = createStaff("support", role = VendorRole.SUPPORT)
        val clinicId = clinic(owner)
        val id = idOf(issue(owner, clinicId).andReturn().response.contentAsString)

        issue(support, clinicId).andExpect { status { isForbidden() } }
        mockMvc.get("/api/v1/licenses/$id/file") { with(asStaff(support)) }.andExpect { status { isOk() } }
    }

    @Test
    fun `max branches cannot be below the clinic's live branches`() {
        val owner = createStaff("owner")
        val clinicId = clinic(owner)
        repeat(2) { n ->
            mockMvc.post("/api/v1/clinics/$clinicId/branches") {
                with(asStaff(owner)); contentType = MediaType.APPLICATION_JSON; content = """{"name":"B$n"}"""
            }
        }

        issue(owner, clinicId).andExpect {
            status { isBadRequest() }
            jsonPath("$.fieldErrors[0].code") { value("validation.below_current_branches") }
            jsonPath("$.fieldErrors[0].params.current") { value(2) }
        }
    }

    @Test
    fun `deleting a clinic revokes its current license`() {
        val owner = createStaff("owner")
        val clinicId = clinic(owner)
        val id = idOf(issue(owner, clinicId).andReturn().response.contentAsString)
        mockMvc.post("/api/v1/clinics/$clinicId/status") {
            with(asStaff(owner)); contentType = MediaType.APPLICATION_JSON; content = """{"status":"ARCHIVED","version":0}"""
        }

        mockMvc.delete("/api/v1/clinics/$clinicId?version=1") { with(asStaff(owner)) }.andExpect { status { isNoContent() } }

        mockMvc.get("/api/v1/licenses/$id") { with(asStaff(owner)) }.andExpect {
            jsonPath("$.status") { value("REVOKED") }
            jsonPath("$.revokeReason") { value("clinic_deleted") }
        }
    }
}
