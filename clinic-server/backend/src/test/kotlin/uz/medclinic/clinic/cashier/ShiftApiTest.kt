package uz.medclinic.clinic.cashier

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

class ShiftApiTest : IntegrationTest() {
    private lateinit var admin: StaffMember
    private lateinit var reception: StaffMember
    private lateinit var cashier: StaffMember
    private lateinit var doctor: StaffMember
    private lateinit var consultation: String
    private lateinit var blood: String
    private lateinit var patientId: String

    @BeforeEach
    fun setUp() {
        pairClinic()
        admin = createStaff()
        reception = createStaff("reception", RoleTemplate.RECEPTION)
        cashier = createStaff("cashier", RoleTemplate.CASHIER)
        doctor = createStaff("doctor", RoleTemplate.DOCTOR)
        val group = id(post("/api/v1/catalog/groups", """{"name":"Xizmatlar"}""", admin))
        consultation = id(post("/api/v1/catalog/services", """{"groupId":"$group","name":"Konsultatsiya","price":150000}""", admin))
        blood = id(post("/api/v1/catalog/services", """{"groupId":"$group","name":"Qon tahlili","price":40000}""", admin))
        patientId = id(post("/api/v1/patients", """{"lastName":"Karimova","firstName":"Dilnoza","gender":"FEMALE","birthDate":"1990-01-01","phone":"901234567"}""", reception))
    }

    private fun post(path: String, json: String, as_: StaffMember): ResultActionsDsl = mockMvc.post(path) {
        with(asStaff(as_)); contentType = MediaType.APPLICATION_JSON; content = json
    }

    private fun json(r: ResultActionsDsl): JsonNode = body(r.andReturn().response.contentAsString)

    private fun id(r: ResultActionsDsl): String = json(r).path("id").asString()

    private fun visit(vararg services: String): JsonNode =
        json(post("/api/v1/visits", """{"patientId":"$patientId","services":[${services.joinToString { """{"serviceId":"$it"}""" }}]}""", reception))

    private fun pay(v: JsonNode, item: Int, method: String) = post(
        "/api/v1/cashier/payments",
        """{"visitId":"${v.path("id").asString()}","itemIds":["${v.path("items")[item].path("id").asString()}"],"method":"$method","amount":${v.path("items")[item].path("amount").asLong()}}""",
        cashier,
    )

    @Test
    fun `a shift counts cash in, refunds and expenses out, and closing shows the difference`() {
        val v = visit(consultation, blood)
        pay(v, 0, "CASH").andExpect { jsonPath("$.code") { value("shift.not_open") } }

        val shift = json(post("/api/v1/cashier/shifts", """{"openingCash":50000}""", cashier).andExpect { status { isCreated() } })
        post("/api/v1/cashier/shifts", "{}", cashier).andExpect { jsonPath("$.code") { value("shift.already_open") } }
        pay(v, 0, "CASH").andExpect { status { isCreated() } }
        pay(v, 1, "CARD").andExpect { status { isCreated() } }

        // Expense: comment is mandatory; recipient from the staff list or typed in.
        post("/api/v1/cashier/shifts/current/expenses", """{"recipientStaffId":"${doctor.id}","amount":20000,"comment":" "}""", cashier)
            .andExpect { jsonPath("$.fieldErrors[0].field") { value("comment") } }
        post("/api/v1/cashier/shifts/current/expenses", """{"recipientStaffId":"${doctor.id}","amount":20000,"comment":"Operatsiya uchun material"}""", cashier)
            .andExpect { jsonPath("$.expenses[0].recipientName") { value("Test Doctor") } }
        post("/api/v1/cashier/shifts/current/expenses", """{"recipientName":"Suv yetkazuvchi","amount":5000,"comment":"Suv"}""", cashier)
            .andExpect { status { isCreated() } }

        // Only the Admin refunds; the cashier pays it out from the open shift.
        val item = v.path("items")[0].path("id").asString()
        post("/api/v1/cashier/refunds", """{"visitItemId":"$item","reason":"Bemor koʻrikdan oldin ketdi"}""", cashier).andExpect { status { isForbidden() } }
        val refund = json(post("/api/v1/cashier/refunds", """{"visitItemId":"$item","reason":"Bemor koʻrikdan oldin ketdi"}""", admin).andExpect { status { isCreated() } })
        post("/api/v1/cashier/refunds", """{"visitItemId":"$item","reason":"Yana"}""", admin).andExpect { jsonPath("$.code") { value("visit.item_not_refundable") } }
        mockMvc.get("/api/v1/cashier/refunds/pending") { with(asStaff(cashier)) }.andExpect { jsonPath("$.length()") { value(1) } }
        post("/api/v1/cashier/refunds/${refund.path("id").asString()}/pay-out", """{"version":0}""", cashier).andExpect { jsonPath("$.status") { value("PAID_OUT") } }
        mockMvc.get("/api/v1/visits/${v.path("id").asString()}") { with(asStaff(reception)) }.andExpect { jsonPath("$.items[0].status") { value("REFUNDED") } }

        // X report: 50 000 + 150 000 cash − 150 000 refund − 25 000 expenses = 25 000 expected.
        mockMvc.get("/api/v1/cashier/shifts/current") { with(asStaff(cashier)) }.andExpect {
            jsonPath("$.totals.cash") { value(150000) }
            jsonPath("$.totals.card") { value(40000) }
            jsonPath("$.totals.refunds") { value(150000) }
            jsonPath("$.totals.expenses") { value(25000) }
            jsonPath("$.expectedCash") { value(25000) }
        }
        val version = json(mockMvc.get("/api/v1/cashier/shifts/current") { with(asStaff(cashier)) }).path("version").asInt()
        post("/api/v1/cashier/shifts/${shift.path("id").asString()}/close", """{"countedCash":24000,"note":"1000 kam","version":$version}""", cashier).andExpect {
            jsonPath("$.status") { value("CLOSED") }
            jsonPath("$.difference") { value(-1000) }
        }
        pay(visit(blood), 0, "CASH").andExpect { jsonPath("$.code") { value("shift.not_open") } }

        mockMvc.get("/api/v1/cashier/shifts?from=2026-11-25&to=2026-11-25") { with(asStaff(admin)) }.andExpect {
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].difference") { value(-1000) }
        }
        mockMvc.get("/api/v1/cashier/shifts?from=2026-11-25&to=2026-11-25") { with(asStaff(cashier)) }.andExpect { status { isForbidden() } }
        val audited = jdbc.sql("select count(*) from audit_log where action in ('shift.opened','shift.expense','shift.closed','refund.approved','refund.paid_out')")
            .query(Long::class.java).single()
        assertThat(audited).isEqualTo(6)
    }

    @Test
    fun `admin's emergency allowances are listed as debts until paid`() {
        val v = visit(blood)
        val item = v.path("items")[0].path("id").asString()
        post("/api/v1/visits/${v.path("id").asString()}/allow", """{"itemIds":["$item"],"reason":"Shoshilinch","version":0}""", admin)
        mockMvc.get("/api/v1/cashier/debts") { with(asStaff(cashier)) }.andExpect {
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].total") { value(40000) }
            jsonPath("$[0].patient.fullName") { value("Karimova Dilnoza") }
        }
        post("/api/v1/cashier/shifts", "{}", cashier)
        pay(json(mockMvc.get("/api/v1/visits/${v.path("id").asString()}") { with(asStaff(reception)) }), 0, "CASH").andExpect { status { isCreated() } }
        mockMvc.get("/api/v1/cashier/debts") { with(asStaff(cashier)) }.andExpect { jsonPath("$.length()") { value(0) } }
    }
}
