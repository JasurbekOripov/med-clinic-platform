package uz.medclinic.clinic.queues

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.JsonNode
import uz.medclinic.clinic.identity.domain.RoleTemplate
import uz.medclinic.clinic.identity.domain.StaffMember
import uz.medclinic.clinic.support.IntegrationTest
import java.time.Duration

/** Payment → one slip per room with a daily number → the room's list → "seen". */
class QueueApiTest : IntegrationTest() {
    private lateinit var admin: StaffMember
    private lateinit var reception: StaffMember
    private lateinit var cashier: StaffMember
    private lateinit var doctor: StaffMember
    private lateinit var consultation: String
    private lateinit var blood: String
    private lateinit var sugar: String
    private lateinit var cardioQueue: String
    private lateinit var labQueue: String

    @BeforeEach
    fun setUp() {
        pairClinic()
        admin = createStaff()
        reception = createStaff("reception", RoleTemplate.RECEPTION)
        cashier = createStaff("cashier", RoleTemplate.CASHIER)
        doctor = createStaff("cardiologist", RoleTemplate.DOCTOR)
        val cons = id(post("/api/v1/catalog/groups", """{"name":"Konsultatsiyalar"}""", admin))
        val lab = id(post("/api/v1/catalog/groups", """{"name":"Laboratoriya"}""", admin))
        consultation = id(post("/api/v1/catalog/services", """{"groupId":"$cons","name":"Kardiolog","price":150000,"perDoctor":true}""", admin))
        blood = id(post("/api/v1/catalog/services", """{"groupId":"$lab","name":"Qon tahlili","price":45000}""", admin))
        sugar = id(post("/api/v1/catalog/services", """{"groupId":"$lab","name":"Glyukoza","price":30000}""", admin))
        cardioQueue = id(post("/api/v1/queues", """{"name":"Kardiolog","prefix":"k","room":"12","doctorId":"${doctor.id}"}""", admin))
        labQueue = id(post("/api/v1/queues", """{"name":"Laboratoriya","prefix":"L","room":"3","serviceIds":["$blood","$sugar"]}""", admin))
        // Payments go into the cashier's open shift.
        post("/api/v1/cashier/shifts", "{}", cashier).andExpect { status { isCreated() } }
    }

    private fun post(path: String, json: String, as_: StaffMember): ResultActionsDsl = mockMvc.post(path) {
        with(asStaff(as_)); contentType = MediaType.APPLICATION_JSON; content = json
    }

    private fun json(r: ResultActionsDsl): JsonNode = body(r.andReturn().response.contentAsString)

    private fun id(r: ResultActionsDsl): String = json(r).path("id").asString()

    /** Registers a patient, orders [services] and pays for all of them; returns the payment. */
    private fun visitPaid(lastName: String, services: String): JsonNode {
        val patient = id(post("/api/v1/patients", """{"lastName":"$lastName","firstName":"Ali","gender":"MALE","birthDate":"1980-01-01","phone":"90${(1000000..9999999).random()}"}""", reception))
        val visit = json(post("/api/v1/visits", """{"patientId":"$patient","services":[$services]}""", reception))
        val items = (0 until visit.path("items").size()).joinToString { "\"${visit.path("items")[it].path("id").asString()}\"" }
        return json(
            post("/api/v1/cashier/payments", """{"visitId":"${visit.path("id").asString()}","itemIds":[$items],"method":"CASH","amount":${visit.path("total").asLong()}}""", cashier)
                .andExpect { status { isCreated() } },
        )
    }

    @Test
    fun `one payment prints one slip per room with the day's next number`() {
        val first = visitPaid("Karimov", """{"serviceId":"$consultation","doctorId":"${doctor.id}"},{"serviceId":"$blood"},{"serviceId":"$sugar"}""")
        val slips = first.path("slips")
        assertThat(slips.size()).isEqualTo(2)
        assertThat(slips[0].path("label").asString()).isEqualTo("K-001")
        assertThat(slips[0].path("room").asString()).isEqualTo("12")
        assertThat(slips[1].path("label").asString()).isEqualTo("L-001")
        assertThat(slips[1].path("lines").size()).isEqualTo(2)

        assertThat(visitPaid("Aliyev", """{"serviceId":"$blood"}""").path("slips")[0].path("label").asString()).isEqualTo("L-002")
        clock.advance(Duration.ofDays(1))
        assertThat(visitPaid("Toshmatov", """{"serviceId":"$sugar"}""").path("slips")[0].path("label").asString()).isEqualTo("L-001")
    }

    @Test
    fun `the doctor sees today's paid patients in number order and marks them seen`() {
        visitPaid("Karimov", """{"serviceId":"$consultation","doctorId":"${doctor.id}"}""")
        visitPaid("Aliyev", """{"serviceId":"$consultation","doctorId":"${doctor.id}"}""")
        val list = json(mockMvc.get("/api/v1/queues/$cardioQueue/tickets") { with(asStaff(doctor)) })
        assertThat((0 until list.size()).map { list[it].path("label").asString() }).containsExactly("K-001", "K-002")
        assertThat(list[0].path("patient").path("fullName").asString()).isEqualTo("Karimov Ali")

        val ticket = list[0].path("id").asString()
        post("/api/v1/queues/tickets/$ticket/done", """{"done":true,"version":0}""", doctor).andExpect { status { isOk() } }
        post("/api/v1/queues/tickets/$ticket/done", """{"done":true,"version":1}""", doctor).andExpect { jsonPath("$.code") { value("ticket.already_done") } }
        post("/api/v1/queues/tickets/$ticket/done", """{"done":false,"version":1}""", doctor).andExpect { status { isOk() } }
        mockMvc.get("/api/v1/queues/$cardioQueue/tickets") { with(asStaff(cashier)) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `admin sets queues up without clashes`() {
        post("/api/v1/queues", """{"name":"UZI","prefix":"L","serviceIds":["$consultation"]}""", admin)
            .andExpect { jsonPath("$.fieldErrors[0].code") { value("queue.prefix_taken") } }
        post("/api/v1/queues", """{"name":"Ikkinchi lab","prefix":"M","serviceIds":["$blood"]}""", admin).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("queue.service_in_other_queue") }
            jsonPath("$.params.queue") { value("Laboratoriya") }
        }
        post("/api/v1/queues", """{"name":"Kardiolog 2","prefix":"N","doctorId":"${doctor.id}"}""", admin)
            .andExpect { jsonPath("$.fieldErrors[0].code") { value("queue.doctor_has_queue") } }
        post("/api/v1/queues", """{"name":"UZI","prefix":"U","serviceIds":["$consultation"]}""", reception).andExpect { status { isForbidden() } }
        mockMvc.get("/api/v1/queues") { with(asStaff(doctor)) }.andExpect {
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].doctorName") { value("Test Cardiologist") }
        }
    }
}
