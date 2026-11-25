package uz.medclinic.clinic.patients

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

class PatientApiTest : IntegrationTest() {
    private lateinit var reception: StaffMember

    @BeforeEach
    fun setUp() {
        pairClinic()
        reception = createStaff("reception", RoleTemplate.RECEPTION)
    }

    private fun create(
        last: String,
        first: String,
        birth: String = "1985-04-12",
        phone: String = "901234567",
        confirmed: Boolean = false,
        as_: StaffMember = reception,
    ) = mockMvc.post("/api/v1/patients") {
        with(asStaff(as_)); contentType = MediaType.APPLICATION_JSON
        content = """{"lastName":"$last","firstName":"$first","gender":"FEMALE","birthDate":"$birth","phone":"$phone","confirmedNotDuplicate":$confirmed}"""
    }

    private fun search(q: String) = mockMvc.get("/api/v1/patients") { param("q", q); with(asStaff(reception)) }

    @Test
    fun `reception registers patients with sequential card numbers`() {
        create("Karimova", "Dilnoza").andExpect {
            status { isCreated() }
            jsonPath("$.cardNo") { value(1) }
            jsonPath("$.fullName") { value("Karimova Dilnoza") }
            jsonPath("$.phone") { value("+998901234567") }
        }
        create("Aliyev", "Vali", phone = "931112233").andExpect { jsonPath("$.cardNo") { value(2) } }
        assertThat(jdbc.sql("select count(*) from audit_log where action = 'patient.created'").query(Long::class.java).single()).isEqualTo(2)
    }

    @Test
    fun `search finds patients across Latin and Cyrillic, by phone digits and by card number`() {
        create("Тошматов", "Ўткир", phone = "901112233")
        create("Karimova", "Dilnoza", phone = "935556677")

        search("toshmatov").andExpect { jsonPath("$.items[0].lastName") { value("Тошматов") } }
        search("Toshmatov Oʻtkir").andExpect { jsonPath("$.totalItems") { value(1) } }
        search("тошма").andExpect { jsonPath("$.totalItems") { value(1) } }
        search("Карим").andExpect { jsonPath("$.items[0].firstName") { value("Dilnoza") } }
        search("Karimva").andExpect { jsonPath("$.items[0].firstName") { value("Dilnoza") } }
        search("5556677").andExpect { jsonPath("$.items[0].firstName") { value("Dilnoza") } }
        search("2").andExpect { jsonPath("$.items[0].firstName") { value("Dilnoza") } }
        search("").andExpect { jsonPath("$.totalItems") { value(2) } }
    }

    @Test
    fun `same name and birth date in another script is reported as a possible duplicate`() {
        create("Karimova", "Dilnoza", birth = "1990-01-05")

        create("Каримова", "Дилноза", birth = "1990-01-05", phone = "977777777").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("patient.possible_duplicate") }
            jsonPath("$.candidates[0].reason") { value("SAME_NAME_AND_BIRTH_DATE") }
            jsonPath("$.candidates[0].patient.cardNo") { value(1) }
        }
        create("Каримова", "Дилноза", birth = "1990-01-05", phone = "977777777", confirmed = true).andExpect { status { isCreated() } }
    }

    @Test
    fun `family members sharing a phone are not duplicates, a similar name on the same phone is`() {
        create("Karimova", "Dilnoza", phone = "901234567")

        create("Karimov", "Aziz", birth = "2015-06-01", phone = "901234567").andExpect { status { isCreated() } }
        create("Karimova", "Dilnoz", birth = "1999-09-09", phone = "901234567").andExpect {
            status { isConflict() }
            jsonPath("$.candidates[0].reason") { value("SAME_PHONE_SIMILAR_NAME") }
        }
    }

    @Test
    fun `validation reports every problem`() {
        mockMvc.post("/api/v1/patients") {
            with(asStaff(reception)); contentType = MediaType.APPLICATION_JSON
            content = """{"lastName":" ","birthDate":"2099-01-01","phone":"123"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.fieldErrors.length()") { value(5) }
        }
    }

    @Test
    fun `edit keeps the card number, only admins delete, deleted patients disappear from search`() {
        val admin = createStaff()
        val id = body(create("Karimova", "Dilnoza").andReturn().response.contentAsString).path("id").asString()

        mockMvc.put("/api/v1/patients/$id") {
            with(asStaff(reception)); contentType = MediaType.APPLICATION_JSON
            content = """{"lastName":"Karimova","firstName":"Dilnoza","patronymic":"Aliyevna","gender":"FEMALE","birthDate":"1985-04-12","phone":"901234567","version":0}"""
        }.andExpect {
            jsonPath("$.fullName") { value("Karimova Dilnoza Aliyevna") }
            jsonPath("$.cardNo") { value(1) }
        }
        mockMvc.delete("/api/v1/patients/$id?version=1") { with(asStaff(reception)) }.andExpect { status { isForbidden() } }
        mockMvc.delete("/api/v1/patients/$id?version=1") { with(asStaff(admin)) }.andExpect { status { isNoContent() } }
        search("karimova").andExpect { jsonPath("$.totalItems") { value(0) } }
        mockMvc.get("/api/v1/patients/$id") { with(asStaff(reception)) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `doctors can view but not register, cashier cannot edit`() {
        val doctor = createStaff("doctor", RoleTemplate.DOCTOR)
        val cashier = createStaff("cashier", RoleTemplate.CASHIER)
        create("Karimova", "Dilnoza", as_ = doctor).andExpect { status { isForbidden() } }
        create("Karimova", "Dilnoza", as_ = cashier).andExpect { status { isForbidden() } }
        mockMvc.get("/api/v1/patients") { with(asStaff(doctor)) }.andExpect { status { isOk() } }
    }
}
