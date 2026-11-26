package uz.medclinic.clinic.catalog

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

class CatalogApiTest : IntegrationTest() {
    private lateinit var admin: StaffMember
    private lateinit var doctor: StaffMember

    @BeforeEach
    fun setUp() {
        pairClinic()
        admin = createStaff()
        doctor = createStaff("cardiologist", RoleTemplate.DOCTOR)
    }

    private fun post(path: String, json: String, as_: StaffMember = admin) = mockMvc.post(path) {
        with(asStaff(as_)); contentType = MediaType.APPLICATION_JSON; content = json
    }

    private fun createGroup(name: String) = body(post("/api/v1/catalog/groups", """{"name":"$name"}""").andReturn().response.contentAsString).path("id").asString()

    @Test
    fun `admin builds a price list with an own consultation price per doctor, every change audited`() {
        val group = createGroup("Konsultatsiyalar")
        val created = post(
            "/api/v1/catalog/services",
            """{"groupId":"$group","code":"K-1","name":"Kardiolog","price":150000,"perDoctor":true,
               "doctorPrices":[{"staffId":"${doctor.id}","price":200000}]}""",
        ).andExpect {
            status { isCreated() }
            jsonPath("$.doctorPrices[0].price") { value(200000) }
        }.andReturn()
        val id = body(created.response.contentAsString).path("id").asString()

        mockMvc.put("/api/v1/catalog/services/$id") {
            with(asStaff(admin)); contentType = MediaType.APPLICATION_JSON
            content = """{"groupId":"$group","code":"K-1","name":"Kardiolog","price":160000,"perDoctor":true,"doctorPrices":[],"version":0}"""
        }.andExpect {
            jsonPath("$.price") { value(160000) }
            jsonPath("$.doctorPrices.length()") { value(0) }
        }
        mockMvc.get("/api/v1/catalog/doctors") { with(asStaff(admin)) }.andExpect {
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].id") { value(doctor.id.toString()) }
        }
        val audited = jdbc.sql("select action from audit_log where entity_type in ('service', 'service_group') order by occurred_at, id")
            .query(String::class.java).list()
        assertThat(audited).containsExactlyInAnyOrder("service_group.created", "service.created", "service.updated")
    }

    @Test
    fun `only price list managers change prices, reception and cashier read them`() {
        val reception = createStaff("reception", RoleTemplate.RECEPTION)
        val cashier = createStaff("cashier", RoleTemplate.CASHIER)
        val group = createGroup("Laboratoriya")
        post("/api/v1/catalog/services", """{"groupId":"$group","name":"Qon tahlili","price":50000}""", as_ = reception)
            .andExpect { status { isForbidden() } }
        post("/api/v1/catalog/discounts", """{"name":"Nafaqaxoʻr","percent":20}""", as_ = cashier).andExpect { status { isForbidden() } }
        mockMvc.get("/api/v1/catalog/groups") { with(asStaff(reception)) }.andExpect { status { isOk() } }
        mockMvc.get("/api/v1/catalog/services") { with(asStaff(cashier)) }.andExpect { status { isOk() } }
        mockMvc.get("/api/v1/catalog/services") { with(asStaff(createStaff("lab", RoleTemplate.LAB))) }.andExpect { status { isOk() } }
        mockMvc.get("/api/v1/catalog/services") { with(asStaff(createStaff("doctor", RoleTemplate.DOCTOR))) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `names and codes are unique, non-empty groups cannot be deleted, unknown doctors are rejected`() {
        val group = createGroup("Laboratoriya")
        post("/api/v1/catalog/groups", """{"name":"laboratoriya"}""").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("catalog.group_name_taken") }
        }
        post("/api/v1/catalog/services", """{"groupId":"$group","code":"L1","name":"Qon tahlili","price":50000}""").andExpect { status { isCreated() } }
        post("/api/v1/catalog/services", """{"groupId":"$group","code":"l1","name":"Siydik tahlili","price":40000}""").andExpect { status { isConflict() } }
        post("/api/v1/catalog/services", """{"groupId":"$group","name":"X","price":1,"perDoctor":true,"doctorPrices":[{"staffId":"${admin.id}","price":1}]}""")
            .andExpect { jsonPath("$.fieldErrors[0].code") { value("catalog.unknown_doctor") } }
        mockMvc.delete("/api/v1/catalog/groups/$group?version=0") { with(asStaff(admin)) }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("catalog.group_not_empty") }
        }
    }

    @Test
    fun `admin manages discount types`() {
        val id = body(post("/api/v1/catalog/discounts", """{"name":"Nafaqaxoʻr","percent":20}""").andExpect { status { isCreated() } }
            .andReturn().response.contentAsString).path("id").asString()
        post("/api/v1/catalog/discounts", """{"name":"Nogironlik","percent":150}""").andExpect { status { isBadRequest() } }
        mockMvc.put("/api/v1/catalog/discounts/$id") {
            with(asStaff(admin)); contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Nafaqaxoʻr","percent":25,"active":false,"version":0}"""
        }.andExpect { jsonPath("$.percent") { value(25) } }
        mockMvc.delete("/api/v1/catalog/discounts/$id?version=1") { with(asStaff(admin)) }.andExpect { status { isNoContent() } }
        mockMvc.get("/api/v1/catalog/discounts") { with(asStaff(admin)) }.andExpect { jsonPath("$.length()") { value(0) } }
    }
}
