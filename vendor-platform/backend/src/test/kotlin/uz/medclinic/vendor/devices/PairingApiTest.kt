package uz.medclinic.vendor.devices

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import uz.medclinic.license.LicenseVerification
import uz.medclinic.license.LicenseVerifier
import uz.medclinic.vendor.devices.application.ClaimThrottle
import uz.medclinic.vendor.identity.domain.VendorRole
import uz.medclinic.vendor.licensing.infrastructure.SigningKeys
import uz.medclinic.vendor.support.TenantIntegrationTest
import java.time.Duration

class PairingApiTest : TenantIntegrationTest() {
    @Autowired
    private lateinit var claimThrottle: ClaimThrottle

    @Autowired
    private lateinit var signingKeys: SigningKeys

    @Test
    fun `support creates a code and the clinic server exchanges it once for a working token`() {
        val owner = createStaff("owner")
        val support = createStaff("support", role = VendorRole.SUPPORT)
        val clinicId = newClinic(owner)
        val branchId = newBranch(owner, clinicId)
        val code = newPairingCode(support, branchId)

        val response = claim(code.lowercase().replace("-", " ")).andExpect {
            status { isOk() }
            jsonPath("$.clinicCode") { value("CL-0001") }
            jsonPath("$.branchCode") { value("CL-0001-01") }
            jsonPath("$.heartbeatIntervalSeconds") { value(60) }
            jsonPath("$.licenseFile") { value(null as Any?) }
        }.andReturn().response.contentAsString
        val token = body(response).path("deviceToken").asString()

        heartbeat(token).andExpect { status { isOk() } }
        claim(code).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("pairing.invalid_code") }
        }
        mockMvc.get("/api/v1/branches/$branchId/pairing-codes") { with(asStaff(support)) }.andExpect {
            jsonPath("$[0].state") { value("CLAIMED") }
            jsonPath("$[0].code") { doesNotExist() }
        }
        val stored = jdbc.sql("select token_hash from device").query(String::class.java).single()
        assertThat(stored).doesNotContain(token).hasSize(64)
    }

    @Test
    fun `expired, cancelled and unknown codes all fail the same way`() {
        val owner = createStaff("owner")
        val branchId = newBranch(owner, newClinic(owner))
        val expired = newPairingCode(owner, branchId)
        clock.advance(Duration.ofHours(24))

        claim(expired).andExpect { jsonPath("$.code") { value("pairing.invalid_code") } }
        claim("AAAA-BBBB-CCCC").andExpect { jsonPath("$.code") { value("pairing.invalid_code") } }

        val cancelled = newPairingCode(owner, branchId)
        val id = jdbc.sql("select id from pairing_code order by created_at desc limit 1").query(java.util.UUID::class.java).single()
        mockMvc.post("/api/v1/pairing-codes/$id/cancel") { with(asStaff(owner)) }.andExpect { status { isNoContent() } }
        claim(cancelled).andExpect { jsonPath("$.code") { value("pairing.invalid_code") } }
    }

    @Test
    fun `repeated invalid claims from one ip are throttled`() {
        claimThrottle.clear()
        repeat(10) { claim("AAAA-BBBB-CCCC") }

        claim("AAAA-BBBB-CCCC").andExpect {
            status { isTooManyRequests() }
            jsonPath("$.code") { value("pairing.too_many_attempts") }
        }
        claimThrottle.clear()
    }

    @Test
    fun `re-pairing a branch revokes the previous server`() {
        val owner = createStaff("owner")
        val branchId = newBranch(owner, newClinic(owner))
        val old = pair(owner, branchId, "OLD-PC")
        val new = pair(owner, branchId, "NEW-PC")

        heartbeat(old.token).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("device.unauthorized") }
        }
        heartbeat(new.token).andExpect { status { isOk() } }
        mockMvc.get("/api/v1/branches/$branchId/devices") { with(asStaff(owner)) }.andExpect {
            jsonPath("$[0].hostname") { value("NEW-PC") }
            jsonPath("$[0].status") { value("ACTIVE") }
            jsonPath("$[1].status") { value("REVOKED") }
            jsonPath("$[1].revokeReason") { value("replaced") }
        }
    }

    @Test
    fun `staff can revoke a device and deleting its branch revokes it too`() {
        val owner = createStaff("owner")
        val clinicId = newClinic(owner)
        val first = pair(owner, newBranch(owner, clinicId, "A"))

        mockMvc.post("/api/v1/devices/${first.deviceId}/revoke") {
            with(asStaff(owner)); contentType = MediaType.APPLICATION_JSON; content = """{"reason":"stolen PC","version":0}"""
        }.andExpect { jsonPath("$.status") { value("REVOKED") } }
        heartbeat(first.token).andExpect { status { isUnauthorized() } }

        val branchB = newBranch(owner, clinicId, "B")
        val second = pair(owner, branchB)
        mockMvc.post("/api/v1/branches/$branchB/status") {
            with(asStaff(owner)); contentType = MediaType.APPLICATION_JSON; content = """{"status":"ARCHIVED","version":0}"""
        }
        mockMvc.delete("/api/v1/branches/$branchB?version=1") { with(asStaff(owner)) }.andExpect { status { isNoContent() } }
        heartbeat(second.token).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `pairing respects the license branch limit and delivers the license`() {
        val owner = createStaff("owner")
        val clinicId = newClinic(owner)
        val branchA = newBranch(owner, clinicId, "A")
        mockMvc.post("/api/v1/clinics/$clinicId/licenses") {
            with(asStaff(owner)); contentType = MediaType.APPLICATION_JSON
            content = """{"plan":"STANDARD","modules":["REGISTRATION"],"maxUsers":5,"maxBranches":1,"startsOn":"2026-11-20","endsOn":"2027-11-19"}"""
        }.andExpect { status { isCreated() } }

        val response = claim(newPairingCode(owner, branchA)).andExpect { status { isOk() } }.andReturn().response.contentAsString
        val licenseFile = body(response).path("licenseFile").asString()
        val verifier = LicenseVerifier(mapOf(signingKeys.keyId to signingKeys.publicKey))
        assertThat(verifier.verify(licenseFile.toByteArray(), clinicId)).isInstanceOf(LicenseVerification.Valid::class.java)

        val token = body(response).path("deviceToken").asString()
        mockMvc.get("/api/device/v1/license") { header("Authorization", "Bearer $token") }.andExpect {
            status { isOk() }
            content { string(licenseFile) }
        }

        // A license for 1 branch cannot have a second paired branch (the branch must exist first to test it).
        jdbc.sql("update license set max_branches = 1").update()
        val branchB = jdbc.sql("insert into branch (id, clinic_id, code, name, status, created_at, updated_at) values (gen_random_uuid(), :c, 'CL-0001-99', 'B', 'ACTIVE', now(), now()) returning id")
            .param("c", clinicId).query(java.util.UUID::class.java).single()
        claim(newPairingCode(owner, branchB)).andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.code") { value("pairing.branch_limit_reached") }
        }
    }

    @Test
    fun `device api rejects missing and malformed tokens, and inactive branches get no codes`() {
        val owner = createStaff("owner")
        val branchId = newBranch(owner, newClinic(owner))

        mockMvc.post("/api/device/v1/heartbeats") { contentType = MediaType.APPLICATION_JSON; content = "{}" }
            .andExpect { status { isUnauthorized() } }
        heartbeat("not-a-token").andExpect { status { isUnauthorized() } }
        heartbeat("dvt1_unknown").andExpect { status { isUnauthorized() } }

        mockMvc.post("/api/v1/branches/$branchId/status") {
            with(asStaff(owner)); contentType = MediaType.APPLICATION_JSON; content = """{"status":"SUSPENDED","version":0}"""
        }
        mockMvc.post("/api/v1/branches/$branchId/pairing-codes") { with(asStaff(owner)) }.andExpect {
            jsonPath("$.code") { value("pairing.branch_not_active") }
        }
    }
}
