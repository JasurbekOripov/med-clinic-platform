package uz.medclinic.clinic.backup

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import uz.medclinic.clinic.identity.domain.RoleTemplate
import uz.medclinic.clinic.identity.domain.StaffMember
import uz.medclinic.clinic.support.IntegrationTest

class BackupApiTest : IntegrationTest() {
    private lateinit var admin: StaffMember

    @BeforeEach
    fun setUp() {
        pairClinic()
        admin = createStaff()
    }

    private fun status() = body(mockMvc.get("/api/v1/backups") { with(asStaff(admin)) }.andReturn().response.contentAsString)

    @Test
    fun `the admin runs a backup now, sees it in the history and the warning goes away`() {
        mockMvc.get("/api/v1/backups/health") { with(asStaff(admin)) }.andExpect { jsonPath("$.stale") { value(true) } }
        mockMvc.post("/api/v1/backups/run") { with(asStaff(admin)) }.andExpect { status { isAccepted() } }
        repeat(100) { if (!status().path("running").asBoolean() && status().path("runs").size() > 0) return@repeat; Thread.sleep(50) }
        val s = status()
        assertThat(s.path("runs")[0].path("status").asString()).isEqualTo("SUCCESS")
        assertThat(s.path("runs")[0].path("fileName").asString()).startsWith("medclinic-").endsWith(".dump")
        assertThat(s.path("stale").asBoolean()).isFalse()
        assertThat(jdbc.sql("select count(*) from audit_log where action = 'backup.requested'").query(Long::class.java).single()).isEqualTo(1)
    }

    @Test
    fun `only the admin sees and changes backups, retention is validated`() {
        val reception = createStaff("reception", RoleTemplate.RECEPTION)
        mockMvc.get("/api/v1/backups") { with(asStaff(reception)) }.andExpect { status { isForbidden() } }
        mockMvc.post("/api/v1/backups/run") { with(asStaff(reception)) }.andExpect { status { isForbidden() } }
        mockMvc.put("/api/v1/backups/settings") {
            with(asStaff(admin)); contentType = MediaType.APPLICATION_JSON; content = """{"retentionDays":1}"""
        }.andExpect { status { isBadRequest() } }
        mockMvc.put("/api/v1/backups/settings") {
            with(asStaff(admin)); contentType = MediaType.APPLICATION_JSON; content = """{"retentionDays":60}"""
        }.andExpect { jsonPath("$.retentionDays") { value(60) } }
    }
}
