package uz.medclinic.clinic.agents

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import uz.medclinic.clinic.identity.domain.RoleTemplate
import uz.medclinic.clinic.identity.domain.StaffMember
import uz.medclinic.clinic.support.IntegrationTest

class AgentApiTest : IntegrationTest() {
    private lateinit var admin: StaffMember

    @BeforeEach
    fun setUp() {
        pairClinic()
        admin = createStaff()
    }

    private fun create(json: String, as_: StaffMember = admin) = mockMvc.post("/api/v1/agents") {
        with(asStaff(as_)); contentType = MediaType.APPLICATION_JSON; content = json
    }

    @Test
    fun `admin manages agents with a fixed fee, every change audited`() {
        val id = body(
            create("""{"name":"Dr. Rahimov (Chilonzor)","phone":"901234567","fee":50000}""").andExpect {
                status { isCreated() }
                jsonPath("$.phone") { value("+998901234567") }
            }.andReturn().response.contentAsString,
        ).path("id").asString()
        mockMvc.put("/api/v1/agents/$id") {
            with(asStaff(admin)); contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Dr. Rahimov (Chilonzor)","fee":70000,"version":0}"""
        }.andExpect {
            jsonPath("$.fee") { value(70000) }
            jsonPath("$.phone") { doesNotExist() }
        }
        create("""{"name":"dr. rahimov (chilonzor)","fee":1}""").andExpect { jsonPath("$.code") { value("agent.name_taken") } }
        create("""{"name":" ","phone":"12","fee":-1}""").andExpect { jsonPath("$.fieldErrors.length()") { value(3) } }
        mockMvc.delete("/api/v1/agents/$id?version=1") { with(asStaff(admin)) }.andExpect { status { isNoContent() } }
        assertThat(jdbc.sql("select count(*) from audit_log where entity_type = 'agent'").query(Long::class.java).single()).isEqualTo(3)
    }

    @Test
    fun `reception sees agents to record referrals but cannot change them`() {
        val reception = createStaff("reception", RoleTemplate.RECEPTION)
        create("""{"name":"Agent","fee":1}""", as_ = reception).andExpect { status { isForbidden() } }
        mockMvc.get("/api/v1/agents") { with(asStaff(reception)) }.andExpect { status { isOk() } }
        mockMvc.get("/api/v1/agents") { with(asStaff(createStaff("doctor", RoleTemplate.DOCTOR))) }.andExpect { status { isForbidden() } }
    }
}
