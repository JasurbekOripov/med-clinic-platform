package uz.medclinic.clinic.visits

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import tools.jackson.databind.JsonNode
import uz.medclinic.clinic.identity.domain.RoleTemplate
import uz.medclinic.clinic.identity.domain.StaffMember
import uz.medclinic.clinic.support.IntegrationTest

/** Reception orders → cashier takes money → doctors see the patient: the owner's payment rules end to end. */
class VisitCashierApiTest : IntegrationTest() {
    private lateinit var admin: StaffMember
    private lateinit var reception: StaffMember
    private lateinit var cashier: StaffMember
    private lateinit var doctor: StaffMember
    private lateinit var patientId: String
    private lateinit var consultation: String
    private lateinit var bloodTest: String
    private lateinit var pensioner: String

    @BeforeEach
    fun setUp() {
        pairClinic()
        admin = createStaff()
        reception = createStaff("reception", RoleTemplate.RECEPTION)
        cashier = createStaff("cashier", RoleTemplate.CASHIER)
        doctor = createStaff("cardiologist", RoleTemplate.DOCTOR)
        val consultations = id(post("/api/v1/catalog/groups", """{"name":"Konsultatsiyalar"}""", admin))
        val lab = id(post("/api/v1/catalog/groups", """{"name":"Laboratoriya"}""", admin))
        consultation = id(
            post(
                "/api/v1/catalog/services",
                """{"groupId":"$consultations","name":"Kardiolog","price":150000,"perDoctor":true,"doctorPrices":[{"staffId":"${doctor.id}","price":200000}]}""",
                admin,
            ),
        )
        bloodTest = id(post("/api/v1/catalog/services", """{"groupId":"$lab","name":"Qon tahlili","price":45000}""", admin))
        pensioner = id(post("/api/v1/catalog/discounts", """{"name":"Nafaqaxoʻr","percent":20}""", admin))
        patientId = id(
            post("/api/v1/patients", """{"lastName":"Karimova","firstName":"Dilnoza","gender":"FEMALE","birthDate":"1950-04-12","phone":"901234567"}""", reception),
        )
    }

    private fun post(path: String, json: String, as_: StaffMember): ResultActionsDsl = mockMvc.post(path) {
        with(asStaff(as_)); contentType = MediaType.APPLICATION_JSON; content = json
    }

    private fun json(r: ResultActionsDsl): JsonNode = body(r.andReturn().response.contentAsString)

    private fun id(r: ResultActionsDsl): String = json(r).path("id").asString()

    private fun createVisit(): JsonNode = json(
        post(
            "/api/v1/visits",
            """{"patientId":"$patientId","discountTypeId":"$pensioner","services":[{"serviceId":"$consultation","doctorId":"${doctor.id}"},{"serviceId":"$bloodTest"}]}""",
            reception,
        ).andExpect { status { isCreated() } },
    )

    @Test
    fun `reception orders services with the doctor's own price and the visit discount`() {
        val visit = createVisit()
        assertThat(visit.path("visitNo").asLong()).isEqualTo(1)
        val items = visit.path("items")
        assertThat(items[0].path("price").asLong()).isEqualTo(200_000)
        assertThat(items[0].path("discount").asLong()).isEqualTo(40_000)
        assertThat(items[0].path("doctorName").asString()).isEqualTo("Test Cardiologist")
        assertThat(items[1].path("amount").asLong()).isEqualTo(36_000)
        assertThat(visit.path("total").asLong()).isEqualTo(196_000)

        post("/api/v1/visits", """{"patientId":"$patientId","services":[{"serviceId":"$consultation"}]}""", reception)
            .andExpect { jsonPath("$.fieldErrors[0].code") { value("catalog.doctor_required") } }
        post("/api/v1/visits", """{"patientId":"$patientId","services":[{"serviceId":"$bloodTest"}]}""", doctor)
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `doctors see the patient only after payment, the cashier is protected against a changed amount`() {
        val visit = createVisit()
        val visitId = visit.path("id").asString()
        val items: List<String> = (0 until visit.path("items").size()).map { visit.path("items")[it].path("id").asString() }

        mockMvc.get("/api/v1/visits?patientId=$patientId") { with(asStaff(doctor)) }.andExpect { jsonPath("$.length()") { value(0) } }
        mockMvc.get("/api/v1/visits/$visitId") { with(asStaff(doctor)) }.andExpect { status { isNotFound() } }

        mockMvc.get("/api/v1/cashier/unpaid") { with(asStaff(cashier)) }.andExpect {
            jsonPath("$.totalItems") { value(1) }
            jsonPath("$.items[0].patient.fullName") { value("Karimova Dilnoza") }
            jsonPath("$.items[0].total") { value(196000) }
        }
        val pay = { amount: Long, ids: List<String> ->
            post(
                "/api/v1/cashier/payments",
                """{"visitId":"$visitId","itemIds":[${ids.joinToString { "\"$it\"" }}],"method":"CASH","amount":$amount}""",
                cashier,
            )
        }
        pay(150_000, listOf(items[0])).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("payment.amount_changed") }
        }
        pay(160_000, listOf(items[0])).andExpect {
            status { isCreated() }
            jsonPath("$.receiptNo") { value(1) }
            jsonPath("$.lines[0].serviceName") { value("Kardiolog") }
            jsonPath("$.cashierName") { value("Test Cashier") }
        }
        pay(160_000, listOf(items[0])).andExpect { jsonPath("$.code") { value("visit.item_not_payable") } }

        mockMvc.get("/api/v1/visits/$visitId") { with(asStaff(doctor)) }.andExpect {
            jsonPath("$.items.length()") { value(1) }
            jsonPath("$.items[0].status") { value("PAID") }
            jsonPath("$.agent") { doesNotExist() }
        }
        post("/api/v1/visits/$visitId/services/${items[0]}/cancel", """{"reason":"Xato","version":1}""", reception)
            .andExpect { jsonPath("$.code") { value("visit.item_already_paid") } }
        post("/api/v1/visits/$visitId/services/${items[0]}/cancel", """{"reason":"Xato","version":0}""", reception)
            .andExpect { status { isConflict() } }

        val paymentId = json(mockMvc.get("/api/v1/cashier/payments?visitId=$visitId") { with(asStaff(reception)) })[0].path("id").asString()
        post("/api/v1/cashier/payments/$paymentId/reprint", "", reception).andExpect { jsonPath("$.receiptNo") { value(1) } }
        post("/api/v1/cashier/payments/$paymentId/reprint", "", doctor).andExpect { status { isForbidden() } }
        assertThat(jdbc.sql("select count(*) from audit_log where action = 'payment.reprinted'").query(Long::class.java).single()).isEqualTo(1)

        val cashierAudit = jdbc.sql("select count(*) from audit_log where action in ('payment.received', 'visit.services_paid')")
            .query(Long::class.java).single()
        assertThat(cashierAudit).isEqualTo(2)
    }

    @Test
    fun `only the admin lets an unpaid patient through, and reception edits only unpaid services`() {
        val visit = createVisit()
        val visitId = visit.path("id").asString()
        val lab = visit.path("items")[1].path("id").asString()

        post("/api/v1/visits/$visitId/allow", """{"itemIds":["$lab"],"reason":"Shoshilinch","version":0}""", reception)
            .andExpect { status { isForbidden() } }
        post("/api/v1/visits/$visitId/allow", """{"itemIds":["$lab"],"reason":"Shoshilinch","version":0}""", admin)
            .andExpect { jsonPath("$.items[1].allowed") { value(true) } }
        mockMvc.get("/api/v1/visits?patientId=$patientId") { with(asStaff(doctor)) }.andExpect {
            jsonPath("$[0].items.length()") { value(1) }
            jsonPath("$[0].items[0].serviceName") { value("Qon tahlili") }
            jsonPath("$[0].unpaid") { value(36000) }
        }

        mockMvc.put("/api/v1/visits/$visitId") {
            with(asStaff(reception)); contentType = MediaType.APPLICATION_JSON; content = """{"version":1}"""
        }.andExpect { jsonPath("$.total") { value(245000) } }
        post("/api/v1/visits/$visitId/services", """{"serviceId":"$bloodTest","version":2}""", reception)
            .andExpect { jsonPath("$.items.length()") { value(3) } }
        post("/api/v1/visits/$visitId/services/$lab/cancel", """{"reason":"Bemor rad etdi","version":3}""", reception)
            .andExpect {
                jsonPath("$.items[1].status") { value("CANCELLED") }
                jsonPath("$.total") { value(245000) }
            }
    }
}
