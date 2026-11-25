package uz.medclinic.clinic.vendorlink

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import uz.medclinic.clinic.support.FakeVendor
import uz.medclinic.clinic.support.IntegrationTest
import uz.medclinic.clinic.support.TestLicenses
import uz.medclinic.clinic.vendorlink.application.HeartbeatService
import java.time.Instant
import java.util.UUID

class SetupAndHeartbeatTest : IntegrationTest() {
    @Autowired
    private lateinit var heartbeat: HeartbeatService

    private val vendor = FakeVendor()

    @AfterEach
    fun stopVendor() = vendor.close()

    private val license1 = TestLicenses.payload(clinicId, serial = 1)

    private fun claimReply() = FakeVendor.Reply(
        200,
        json.writeValueAsString(
            mapOf(
                "deviceId" to UUID.randomUUID(), "clinicId" to clinicId, "clinicCode" to "CL-0001", "branchId" to branchId,
                "branchCode" to "CL-0001-01", "deviceToken" to "dvt1_secret", "heartbeatIntervalSeconds" to 60,
                "licenseFile" to String(TestLicenses.file(license1)), "clinicName" to "Shifo Med", "branchName" to "Markaziy",
            ),
        ),
    )

    private fun pairVia(code: String = "K7QM-2XRD-9WTF") = mockMvc.post("/api/v1/setup/pair") {
        with(anonymousCsrf()); contentType = MediaType.APPLICATION_JSON
        content = """{"vendorUrl":"${vendor.url}","code":"$code"}"""
    }

    @Test
    fun `first run - pair with the vendor, create the admin, then setup is closed`() {
        vendor.on("POST", "/api/device/v1/pairing/claim") { claimReply() }

        mockMvc.get("/api/v1/setup/status").andExpect { jsonPath("$.paired") { value(false) } }
        pairVia().andExpect {
            status { isOk() }
            jsonPath("$.paired") { value(true) }
            jsonPath("$.hasAdmin") { value(false) }
            jsonPath("$.clinicCode") { value("CL-0001") }
        }
        assertThat(vendor.calls.single().body).contains("K7QM-2XRD-9WTF")
        mockMvc.get("/api/v1/license/status").andExpect { jsonPath("$.state") { value("ACTIVE") } }
        mockMvc.get("/api/v1/settings/public").andExpect { jsonPath("$.name") { value("Shifo Med") } }

        mockMvc.post("/api/v1/setup/admin") {
            with(anonymousCsrf()); contentType = MediaType.APPLICATION_JSON
            content = """{"fullName":"Aliyev Vali","login":"admin","password":"Admin123","vendorSupportPassword":"Vendor123"}"""
        }.andExpect { jsonPath("$.completed") { value(true) } }

        login("admin", "Admin123")
        login("vendor.support", "Vendor123")
        pairVia().andExpect { jsonPath("$.code") { value("setup.already_paired") } }
        mockMvc.post("/api/v1/setup/admin") {
            with(anonymousCsrf()); contentType = MediaType.APPLICATION_JSON
            content = """{"fullName":"Intruder","login":"intruder","password":"Intruder123"}"""
        }.andExpect { jsonPath("$.code") { value("setup.already_completed") } }
        val stored = jdbc.sql("select device_token_enc from installation").query(ByteArray::class.java).single()
        assertThat(String(stored, Charsets.ISO_8859_1)).doesNotContain("dvt1_secret")
    }

    @Test
    fun `vendor errors keep their code, an unreachable vendor is reported`() {
        vendor.on("POST", "/api/device/v1/pairing/claim") { FakeVendor.Reply(401, """{"code":"pairing.invalid_code"}""") }
        pairVia().andExpect { jsonPath("$.code") { value("pairing.invalid_code") } }

        mockMvc.post("/api/v1/setup/pair") {
            with(anonymousCsrf()); contentType = MediaType.APPLICATION_JSON
            content = """{"vendorUrl":"http://127.0.0.1:1","code":"AAAA-BBBB-CCCC"}"""
        }.andExpect { jsonPath("$.code") { value("vendor.unreachable") } }
        mockMvc.get("/api/v1/setup/status").andExpect { jsonPath("$.paired") { value(false) } }
    }

    @Test
    fun `heartbeat sends technical data only and installs a renewed license`() {
        pairClinic(license1, vendorUrl = vendor.url)
        val renewed = TestLicenses.payload(clinicId, serial = 2, validUntil = Instant.parse("2028-01-01T00:00:00Z"), maxUsers = 99)
        vendor.on("POST", "/api/device/v1/heartbeats") {
            FakeVendor.Reply(200, """{"serverTime":"${clock.instant()}","nextHeartbeatInSeconds":60,"license":{"currentLicenseId":"${renewed.licenseId}","installedLicenseStatus":"SUPERSEDED"}}""")
        }
        vendor.on("GET", "/api/device/v1/license") { FakeVendor.Reply(200, String(TestLicenses.file(renewed))) }

        assertThat(heartbeat.beat()).isTrue()

        val sent = vendor.calls.first { it.path.endsWith("/heartbeats") }
        assertThat(sent.authorization).isEqualTo("Bearer dvt1_test")
        assertThat(body(sent.body).path("installedLicenseId").asString()).isEqualTo(license1.licenseId.toString())
        mockMvc.get("/api/v1/license/status").andExpect { jsonPath("$.maxUsers") { value(99) } }
    }

    @Test
    fun `a license revoked by the vendor stops the clinic`() {
        pairClinic(license1, vendorUrl = vendor.url)
        val admin = createStaff()
        vendor.on("POST", "/api/device/v1/heartbeats") {
            FakeVendor.Reply(200, """{"serverTime":"${clock.instant()}","nextHeartbeatInSeconds":60,"license":{"currentLicenseId":null,"installedLicenseStatus":"REVOKED"}}""")
        }

        heartbeat.beat()

        mockMvc.get("/api/v1/staff") { with(asStaff(admin)) }.andExpect { jsonPath("$.code") { value("license.revoked") } }
    }

    @Test
    fun `offline vendor is recorded and the clinic keeps working`() {
        pairClinic(license1, vendorUrl = "http://127.0.0.1:1")
        val admin = createStaff()

        assertThat(heartbeat.beat()).isFalse()

        mockMvc.get("/api/v1/license/vendor-link") { with(asStaff(admin)) }.andExpect {
            jsonPath("$.lastErrorCode") { value("vendor.unreachable") }
            jsonPath("$.lastSuccessAt") { doesNotExist() }
        }
        mockMvc.get("/api/v1/staff") { with(asStaff(admin)) }.andExpect { status { isOk() } }
    }
}
