package uz.medclinic.clinic.lab

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import uz.medclinic.clinic.identity.domain.RoleTemplate
import uz.medclinic.clinic.identity.domain.StaffMember
import uz.medclinic.clinic.support.IntegrationTest

/** Values in these forms are arbitrary test numbers, not medical reference ranges. */
class LabTemplateApiTest : IntegrationTest() {
    private lateinit var admin: StaffMember
    private lateinit var lab: StaffMember
    private lateinit var blood: String

    @BeforeEach
    fun setUp() {
        pairClinic()
        admin = createStaff()
        lab = createStaff("lab", RoleTemplate.LAB)
        val group = body(post("/api/v1/catalog/groups", """{"name":"Laboratoriya"}""", admin).andReturn().response.contentAsString).path("id").asString()
        blood = body(post("/api/v1/catalog/services", """{"groupId":"$group","name":"Qon tahlili","price":45000}""", admin).andReturn().response.contentAsString).path("id").asString()
    }

    private fun post(path: String, json: String, as_: StaffMember): ResultActionsDsl = mockMvc.post(path) {
        with(asStaff(as_)); contentType = MediaType.APPLICATION_JSON; content = json
    }

    private val rows = """[
        {"id":"00000000-0000-7000-8000-000000000001","kind":"HEADER","name":"Umumiy"},
        {"id":"00000000-0000-7000-8000-000000000002","kind":"NUMBER","name":" Test X ","unit":"g/L","decimals":0,
         "ranges":[{"low":100,"high":200},{"sex":"FEMALE","ageFrom":{"value":18,"unit":"YEARS"},"state":"PREGNANCY_T2","low":105,"high":140},
                   {"kind":"CRITICAL","low":60,"high":250}]},
        {"id":"00000000-0000-7000-8000-000000000003","kind":"POS_NEG","name":"HBsAg","ranges":[{"text":"manfiy"}]}
    ]"""

    @Test
    fun `the lab creates a form with qualified ranges, editing rows makes a new version`() {
        val created = body(
            post("/api/v1/lab/templates", """{"name":"Umumiy qon tahlili","serviceIds":["$blood"],"rows":$rows}""", lab)
                .andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        )
        assertThat(created.path("versionNo").asInt()).isEqualTo(1)
        assertThat(created.path("rows")[1].path("name").asString()).isEqualTo("Test X")
        assertThat(created.path("rows")[1].path("ranges")[1].path("state").asString()).isEqualTo("PREGNANCY_T2")
        val id = created.path("id").asString()

        // Renaming only keeps the version; changing rows creates version 2.
        val renamed = body(mockMvc.put("/api/v1/lab/templates/$id") {
            with(asStaff(lab)); contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Qon tahlili (UAK)","serviceIds":["$blood"],"rows":$rows,"version":0}"""
        }.andReturn().response.contentAsString)
        assertThat(renamed.path("versionNo").asInt()).isEqualTo(1)
        val edited = body(mockMvc.put("/api/v1/lab/templates/$id") {
            with(asStaff(lab)); contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Qon tahlili (UAK)","serviceIds":["$blood"],"rows":${rows.replace("\"high\":200", "\"high\":210")},"version":1}"""
        }.andReturn().response.contentAsString)
        assertThat(edited.path("versionNo").asInt()).isEqualTo(2)
        assertThat(jdbc.sql("select count(*) from lab_template_version").query(Long::class.java).single()).isEqualTo(2)

        post("/api/v1/lab/templates", """{"name":"Boshqa","serviceIds":["$blood"],"rows":$rows}""", lab).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("lab.service_has_form") }
        }
    }

    @Test
    fun `invalid forms report the exact cell, other roles cannot edit`() {
        val bad = """[{"id":"00000000-0000-7000-8000-000000000002","kind":"NUMBER","name":"X","ranges":[{"low":5,"high":1}]}]"""
        post("/api/v1/lab/templates", """{"name":"X","rows":$bad}""", lab).andExpect {
            status { isBadRequest() }
            jsonPath("$.fieldErrors[0].field") { value("rows[0].ranges[0].high") }
            jsonPath("$.fieldErrors[0].code") { value("lab.range_low_above_high") }
        }
        post("/api/v1/lab/templates", """{"name":"X","rows":$rows}""", createStaff("reception", RoleTemplate.RECEPTION))
            .andExpect { status { isForbidden() } }
        mockMvc.get("/api/v1/lab/templates") { with(asStaff(createStaff("doctor", RoleTemplate.DOCTOR))) }.andExpect { status { isForbidden() } }
    }
}
