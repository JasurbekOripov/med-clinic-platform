package uz.medclinic.clinic.conclusions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import tools.jackson.databind.JsonNode
import uz.medclinic.clinic.identity.domain.RoleTemplate
import uz.medclinic.clinic.identity.domain.StaffMember
import uz.medclinic.clinic.support.IntegrationTest
import java.time.Duration

class ConclusionApiTest : IntegrationTest() {
    private lateinit var admin: StaffMember
    private lateinit var reception: StaffMember
    private lateinit var cashier: StaffMember
    private lateinit var doctor: StaffMember
    private lateinit var otherDoctor: StaffMember
    private lateinit var patientId: String
    private lateinit var visit: JsonNode

    @BeforeEach
    fun setUp() {
        pairClinic()
        admin = createStaff()
        reception = createStaff("reception", RoleTemplate.RECEPTION)
        cashier = createStaff("cashier", RoleTemplate.CASHIER)
        doctor = createStaff("cardiologist", RoleTemplate.DOCTOR)
        otherDoctor = createStaff("neurologist", RoleTemplate.DOCTOR)
        val group = id(post("/api/v1/catalog/groups", """{"name":"Konsultatsiyalar"}""", admin))
        val service = id(post("/api/v1/catalog/services", """{"groupId":"$group","name":"Kardiolog","price":150000,"perDoctor":true}""", admin))
        patientId = id(post("/api/v1/patients", """{"lastName":"Karimova","firstName":"Dilnoza","gender":"FEMALE","birthDate":"1955-04-12","phone":"901234567"}""", reception))
        visit = json(post("/api/v1/visits", """{"patientId":"$patientId","services":[{"serviceId":"$service","doctorId":"${doctor.id}"}]}""", reception))
        // Payments go into the cashier's open shift.
        post("/api/v1/cashier/shifts", "{}", cashier).andExpect { status { isCreated() } }
    }

    private fun post(path: String, body: String, as_: StaffMember): ResultActionsDsl = mockMvc.post(path) {
        with(asStaff(as_)); contentType = MediaType.APPLICATION_JSON; content = body
    }

    private fun json(r: ResultActionsDsl): JsonNode = body(r.andReturn().response.contentAsString)

    private fun id(r: ResultActionsDsl): String = json(r).path("id").asString()

    private val itemId get() = visit.path("items")[0].path("id").asString()

    private fun pay() = post(
        "/api/v1/cashier/payments",
        """{"visitId":"${visit.path("id").asString()}","itemIds":["$itemId"],"method":"CASH","amount":${visit.path("total").asLong()}}""",
        cashier,
    ).andExpect { status { isCreated() } }

    private fun write(as_: StaffMember = doctor) =
        post("/api/v1/conclusions", """{"visitItemId":"$itemId","title":"Kardiolog xulosasi","body":"Shikoyatlar: bosh ogʻrigʻi.\nTashxis: gipertoniya."}""", as_)

    @Test
    fun `only after payment, only the chosen doctor, once per service`() {
        write().andExpect { jsonPath("$.fieldErrors[0].code") { value("conclusion.service_not_available") } }
        pay()
        write(otherDoctor).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("conclusion.not_your_patient") }
        }
        write(reception).andExpect { status { isForbidden() } }
        write().andExpect {
            status { isCreated() }
            jsonPath("$.authorName") { value("Test Cardiologist") }
            jsonPath("$.body") { value("Shikoyatlar: bosh ogʻrigʻi.\nTashxis: gipertoniya.") }
            jsonPath("$.editable") { value(true) }
        }
        write().andExpect { jsonPath("$.code") { value("conclusion.already_written") } }
    }

    @Test
    fun `the author edits on the same day, afterwards only notes, others read the history`() {
        pay()
        val id = id(write())
        mockMvc.put("/api/v1/conclusions/$id") {
            with(asStaff(otherDoctor)); contentType = MediaType.APPLICATION_JSON
            content = """{"title":"X","body":"Y","version":0}"""
        }.andExpect { jsonPath("$.code") { value("conclusion.not_author") } }
        mockMvc.put("/api/v1/conclusions/$id") {
            with(asStaff(doctor)); contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Kardiolog xulosasi","body":"Tashxis: gipertoniya II daraja.","version":0}"""
        }.andExpect { jsonPath("$.version") { value(1) } }

        clock.advance(Duration.ofDays(1))
        mockMvc.put("/api/v1/conclusions/$id") {
            with(asStaff(doctor)); contentType = MediaType.APPLICATION_JSON
            content = """{"title":"X","body":"Y","version":1}"""
        }.andExpect { jsonPath("$.code") { value("conclusion.locked") } }
        post("/api/v1/conclusions/$id/notes", """{"text":"EKG natijasi: normal."}""", doctor).andExpect {
            jsonPath("$.notes[0].text") { value("EKG natijasi: normal.") }
            jsonPath("$.editable") { value(false) }
        }

        mockMvc.get("/api/v1/conclusions?patientId=$patientId") { with(asStaff(otherDoctor)) }.andExpect { jsonPath("$.length()") { value(1) } }
        mockMvc.get("/api/v1/conclusions?patientId=$patientId") { with(asStaff(reception)) }.andExpect { status { isOk() } }
        mockMvc.get("/api/v1/conclusions?patientId=$patientId") { with(asStaff(cashier)) }.andExpect { status { isForbidden() } }
        mockMvc.get("/api/v1/conclusions/$id/signature") { with(asStaff(reception)) }.andExpect { status { isNotFound() } }
        val audited = jdbc.sql("select count(*) from audit_log where entity_type = 'conclusion'").query(Long::class.java).single()
        assertThat(audited).isEqualTo(3)
    }

    @Test
    fun `templates are private to their doctor`() {
        val t = json(post("/api/v1/conclusions/templates", """{"name":"Normal EKG","body":"Sinus ritmi, YuQS 72."}""", doctor).andExpect { status { isCreated() } })
        post("/api/v1/conclusions/templates", """{"name":"normal ekg","body":"x"}""", doctor)
            .andExpect { jsonPath("$.code") { value("conclusion.template_name_taken") } }
        mockMvc.get("/api/v1/conclusions/templates") { with(asStaff(otherDoctor)) }.andExpect { jsonPath("$.length()") { value(0) } }
        mockMvc.delete("/api/v1/conclusions/templates/${t.path("id").asString()}?version=0") { with(asStaff(otherDoctor)) }
            .andExpect { status { isNotFound() } }
        mockMvc.delete("/api/v1/conclusions/templates/${t.path("id").asString()}?version=0") { with(asStaff(doctor)) }
            .andExpect { status { isNoContent() } }
    }
}
