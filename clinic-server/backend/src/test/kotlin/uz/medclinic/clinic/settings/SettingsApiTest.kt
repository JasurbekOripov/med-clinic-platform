package uz.medclinic.clinic.settings

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.put
import uz.medclinic.clinic.identity.domain.RoleTemplate
import uz.medclinic.clinic.support.IntegrationTest

class SettingsApiTest : IntegrationTest() {
    @BeforeEach
    fun paired() = pairClinic()

    private val hours = (listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY")
        .joinToString(",") { """{"day":"$it","closed":false,"opensAt":"08:00","closesAt":"17:00"}""" }) +
        """,{"day":"SUNDAY","closed":true}"""

    @Test
    fun `public settings work without login and show the clinic name from pairing`() {
        mockMvc.get("/api/v1/settings/public").andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Shifo Med") }
            jsonPath("$.idleLockMinutes") { value(10) }
            jsonPath("$.hasLogo") { value(false) }
        }
    }

    @Test
    fun `admin updates settings with validation, others cannot`() {
        val admin = createStaff()
        val cashier = createStaff("cashier", RoleTemplate.CASHIER)

        mockMvc.put("/api/v1/settings") {
            with(asStaff(admin)); contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Shifo Med Plus","phone":"+998 71 200 00 00","receiptFooter":"Rahmat!\nSogʻ boʻling","workingHours":[$hours],"idleLockMinutes":5,"version":0}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Shifo Med Plus") }
            jsonPath("$.phone") { value("+998712000000") }
            jsonPath("$.receiptFooter") { value("Rahmat!\nSogʻ boʻling") }
            jsonPath("$.workingHours[6].closed") { value(true) }
            jsonPath("$.version") { value(1) }
        }
        mockMvc.put("/api/v1/settings") {
            with(asStaff(admin)); contentType = MediaType.APPLICATION_JSON
            content = """{"name":"","phone":"12","workingHours":[{"day":"MONDAY","closed":false,"opensAt":"18:00","closesAt":"08:00"}],"idleLockMinutes":0,"version":1}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.fieldErrors.length()") { value(5) }
        }
        mockMvc.put("/api/v1/settings") {
            with(asStaff(cashier)); contentType = MediaType.APPLICATION_JSON; content = """{"name":"X","version":1}"""
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `logo upload is public to read and validated by content`() {
        val admin = createStaff()
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 16)

        mockMvc.multipart("/api/v1/settings/logo") {
            file(MockMultipartFile("file", "logo.jpg", "image/jpeg", jpeg)); with(asStaff(admin)); with { it.method = "PUT"; it }
        }.andExpect { status { isNoContent() } }
        mockMvc.get("/api/v1/settings/logo").andExpect { status { isOk() }; content { contentType("image/jpeg") } }
        mockMvc.get("/api/v1/settings/public").andExpect { jsonPath("$.hasLogo") { value(true) } }
    }
}
