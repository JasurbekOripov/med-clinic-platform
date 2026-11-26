package uz.medclinic.clinic.lab

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

/** Range values here are arbitrary test numbers, not medical reference ranges. */
class LabResultApiTest : IntegrationTest() {
    private lateinit var admin: StaffMember
    private lateinit var reception: StaffMember
    private lateinit var cashier: StaffMember
    private lateinit var lab: StaffMember
    private lateinit var doctor: StaffMember
    private lateinit var blood: String
    private lateinit var xray: String
    private lateinit var patientId: String

    private val number = "00000000-0000-7000-8000-000000000002"
    private val colour = "00000000-0000-7000-8000-000000000003"
    private val hbs = "00000000-0000-7000-8000-000000000004"

    @BeforeEach
    fun setUp() {
        pairClinic()
        admin = createStaff()
        reception = createStaff("reception", RoleTemplate.RECEPTION)
        cashier = createStaff("cashier", RoleTemplate.CASHIER)
        lab = createStaff("lab", RoleTemplate.LAB)
        doctor = createStaff("doctor", RoleTemplate.DOCTOR)
        val group = id(post("/api/v1/catalog/groups", """{"name":"Laboratoriya"}""", admin))
        blood = id(post("/api/v1/catalog/services", """{"groupId":"$group","name":"Qon tahlili","price":45000}""", admin))
        xray = id(post("/api/v1/catalog/services", """{"groupId":"$group","name":"Rentgen","price":80000}""", admin))
        post(
            "/api/v1/lab/templates",
            """{"name":"Qon tahlili","serviceIds":["$blood"],"rows":[
                {"id":"$number","kind":"NUMBER","name":"Test X","unit":"g/L","decimals":0,
                 "ranges":[{"low":100,"high":200},{"sex":"FEMALE","state":"PREGNANCY_T2","low":105,"high":140},{"kind":"CRITICAL","low":60,"high":250}]},
                {"id":"$colour","kind":"CHOICE","name":"Rangi","options":[{"label":"Sariq","normal":true},{"label":"Qizil","normal":false}]},
                {"id":"$hbs","kind":"POS_NEG","name":"HBsAg"}]}""",
            lab,
        ).andExpect { status { isCreated() } }
        patientId = id(post("/api/v1/patients", """{"lastName":"Karimova","firstName":"Dilnoza","gender":"FEMALE","birthDate":"1990-03-01","phone":"901234567"}""", reception))
    }

    private fun post(path: String, json: String, as_: StaffMember): ResultActionsDsl = mockMvc.post(path) {
        with(asStaff(as_)); contentType = MediaType.APPLICATION_JSON; content = json
    }

    private fun json(r: ResultActionsDsl): JsonNode = body(r.andReturn().response.contentAsString)

    private fun id(r: ResultActionsDsl): String = json(r).path("id").asString()

    private fun visit(vararg services: String): JsonNode =
        json(post("/api/v1/visits", """{"patientId":"$patientId","services":[${services.joinToString { """{"serviceId":"$it"}""" }}]}""", reception))

    private fun payAll(v: JsonNode) {
        val items = (0 until v.path("items").size()).joinToString { "\"${v.path("items")[it].path("id").asString()}\"" }
        post("/api/v1/cashier/payments", """{"visitId":"${v.path("id").asString()}","itemIds":[$items],"method":"CASH","amount":${v.path("total").asLong()}}""", cashier)
            .andExpect { status { isCreated() } }
    }

    private fun work(): JsonNode = json(mockMvc.get("/api/v1/lab/work") { with(asStaff(lab)) })

    /** PUT saves a draft; with [path] "/correct" it POSTs a correction of a ready result. */
    private fun save(id: String, version: Int, values: String, state: String = "PREGNANCY_T2", extra: String = "", path: String = ""): ResultActionsDsl {
        val body = """{"state":"$state","values":$values,"comment":"Qayta topshirish tavsiya etiladi"$extra,"version":$version}"""
        return if (path.isEmpty()) {
            mockMvc.put("/api/v1/lab/results/$id") { with(asStaff(lab)); contentType = MediaType.APPLICATION_JSON; content = body }
        } else {
            post("/api/v1/lab/results/$id$path", body, lab)
        }
    }

    @Test
    fun `paid services with a form reach the lab, norms follow the patient's state, flags are computed by the server`() {
        payAll(visit(blood, xray))
        val list = work()
        assertThat(list.size()).isEqualTo(1) // the x-ray has no lab form
        assertThat(list[0].path("status").asString()).isEqualTo("WAITING")
        val id = list[0].path("id").asString()

        // Doctors do not see work in progress.
        mockMvc.get("/api/v1/lab/results/$id") { with(asStaff(doctor)) }.andExpect { status { isNotFound() } }

        // Norms preview for a pregnant patient before anything is saved.
        mockMvc.get("/api/v1/lab/results/$id?state=PREGNANCY_T2") { with(asStaff(lab)) }.andExpect {
            jsonPath("$.usesStates") { value(true) }
            jsonPath("$.rows[0].norm") { value("105 – 140") }
            jsonPath("$.rows[0].criticalLow") { value(60) }
        }

        save(id, 0, """{"$colour":{"choice":"Yashil"}}""").andExpect { jsonPath("$.fieldErrors[0].field") { value("values.$colour") } }
        save(id, 0, """{"$number":{"number":99},"$colour":{"choice":"Qizil"},"$hbs":{"positive":false}}""").andExpect {
            jsonPath("$.status") { value("DRAFT") }
            jsonPath("$.rows[0].flag") { value("LOW") }
            jsonPath("$.rows[1].flag") { value("ABNORMAL") }
            jsonPath("$.rows[2].flag") { value("NORMAL") }
        }
        save(id, 1, """{"$number":{"number":50}}""", state = "NONE").andExpect {
            jsonPath("$.rows[0].flag") { value("CRITICAL_LOW") }
            jsonPath("$.rows[0].norm") { value("100 – 200") }
        }
        post("/api/v1/lab/results/$id/ready", """{"version":2}""", lab).andExpect { jsonPath("$.readyByName") { value("Test Lab") } }
        save(id, 3, """{"$number":{"number":120}}""").andExpect { jsonPath("$.code") { value("lab.result_ready") } }

        // Corrections after ready need a reason and are marked.
        save(id, 3, """{"$number":{"number":120}}""", path = "/correct").andExpect { jsonPath("$.fieldErrors[0].field") { value("reason") } }
        save(id, 3, """{"$number":{"number":120}}""", extra = ""","reason":"Notoʻgʻri kiritilgan"""", path = "/correct").andExpect {
            jsonPath("$.correctionReason") { value("Notoʻgʻri kiritilgan") }
            jsonPath("$.rows[0].flag") { value("NORMAL") }
        }

        mockMvc.get("/api/v1/lab/results?patientId=$patientId") { with(asStaff(doctor)) }.andExpect {
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].rows[0].value.number") { value(120) }
        }
        mockMvc.get("/api/v1/lab/results?patientId=$patientId") { with(asStaff(reception)) }.andExpect { status { isOk() } }
        mockMvc.get("/api/v1/lab/results?patientId=$patientId") { with(asStaff(cashier)) }.andExpect { status { isForbidden() } }
        val audited = jdbc.sql("select count(*) from audit_log where entity_type = 'lab_result'").query(Long::class.java).single()
        assertThat(audited).isEqualTo(2)
    }

    @Test
    fun `an emergency allowed by the admin reaches the lab before payment, payment later does not duplicate it`() {
        val v = visit(blood)
        val item = v.path("items")[0].path("id").asString()
        post("/api/v1/visits/${v.path("id").asString()}/allow", """{"itemIds":["$item"],"reason":"Shoshilinch","version":0}""", admin)
        assertThat(work().size()).isEqualTo(1)
        val paid = json(mockMvc.get("/api/v1/visits/${v.path("id").asString()}") { with(asStaff(reception)) })
        payAll(paid)
        assertThat(work().size()).isEqualTo(1)
        val empty = work()[0].path("id").asString()
        post("/api/v1/lab/results/$empty/ready", """{"version":0}""", lab).andExpect { jsonPath("$.code") { value("lab.result_empty") } }
    }
}
