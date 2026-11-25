package uz.medclinic.clinic.identity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import uz.medclinic.clinic.identity.domain.RoleTemplate
import uz.medclinic.clinic.support.IntegrationTest
import uz.medclinic.clinic.support.TestLicenses
import java.util.UUID

class IdentityApiTest : IntegrationTest() {
    @BeforeEach
    fun paired() = pairClinic()

    private fun roleId(template: RoleTemplate): UUID = roleRepository.findByTemplate(template)!!.id

    private fun createVia(adminLogin: String = "admin", login: String, template: RoleTemplate, extra: String = "") =
        mockMvc.post("/api/v1/staff") {
            with(asStaff(staffRepository.findByLogin(adminLogin)!!)); contentType = MediaType.APPLICATION_JSON
            content = """{"login":"$login","fullName":"Karimova Dilnoza","position":"Hamshira","phone":"901112233","roleId":"${roleId(template)}"$extra}"""
        }

    @Test
    fun `role templates are seeded, admin and vendor support are locked system roles`() {
        val admin = createStaff()

        mockMvc.get("/api/v1/roles") { with(asStaff(admin)) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(7) }
            jsonPath("$[?(@.template == 'ADMIN')].system") { value(true) }
            jsonPath("$[?(@.template == 'VENDOR_SUPPORT')].assignable") { value(false) }
            jsonPath("$[?(@.template == 'CASHIER')].permissions[*]") { value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("REFUND"))) }
        }
        val adminRole = roleRepository.findByTemplate(RoleTemplate.ADMIN)!!
        mockMvc.put("/api/v1/roles/${adminRole.id}") {
            with(asStaff(admin)); contentType = MediaType.APPLICATION_JSON
            content = """{"permissions":[],"version":0}"""
        }.andExpect { jsonPath("$.code") { value("role.system_locked") } }
    }

    @Test
    fun `admin creates a receptionist with a one-time password and per-person permission changes`() {
        createStaff()

        val created = createVia(login = "dilnoza", template = RoleTemplate.RECEPTION, extra = ""","granted":["PAYMENT_ACCEPT"],"denied":["QUEUE_MANAGE"]""")
            .andExpect {
                status { isCreated() }
                jsonPath("$.staff.phone") { value("+998901112233") }
                jsonPath("$.staff.mustChangePassword") { value(true) }
            }.andReturn().response.contentAsString
        val password = body(created).path("temporaryPassword").asString()

        val me = login("dilnoza", password)
        mockMvc.get("/api/v1/auth/me") { with(me.apply()) }.andExpect {
            jsonPath("$.roleTemplate") { value("RECEPTION") }
            jsonPath("$.permissions") { value(org.hamcrest.Matchers.hasItem("PAYMENT_ACCEPT")) }
            jsonPath("$.permissions") { value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("QUEUE_MANAGE"))) }
            jsonPath("$.permissions") { value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("REFUND"))) }
        }
    }

    @Test
    fun `receptionist and cashier cannot manage staff or see the audit log`() {
        val reception = createStaff("reception", RoleTemplate.RECEPTION)
        val cashier = createStaff("cashier", RoleTemplate.CASHIER)

        listOf(reception, cashier).forEach { s ->
            mockMvc.get("/api/v1/staff") { with(asStaff(s)) }.andExpect { status { isForbidden() } }
            mockMvc.get("/api/v1/audit") { with(asStaff(s)) }.andExpect { status { isForbidden() } }
        }
    }

    @Test
    fun `vendor support role cannot be given to staff`() {
        createStaff()
        createVia(login = "fake.vendor", template = RoleTemplate.VENDOR_SUPPORT).andExpect {
            status { isBadRequest() }
            jsonPath("$.fieldErrors[0].code") { value("validation.role_not_assignable") }
        }
    }

    @Test
    fun `the last active admin cannot be demoted or deactivated`() {
        val admin = createStaff()
        val other = createStaff("doctor", RoleTemplate.DOCTOR)

        mockMvc.put("/api/v1/staff/${admin.id}") {
            with(asStaff(admin)); contentType = MediaType.APPLICATION_JSON
            content = """{"fullName":"Admin","roleId":"${roleId(RoleTemplate.DOCTOR)}","version":0}"""
        }.andExpect { jsonPath("$.code") { value("staff.last_admin") } }
        mockMvc.post("/api/v1/staff/${admin.id}/deactivate") {
            with(asStaff(admin)); contentType = MediaType.APPLICATION_JSON; content = """{"version":0}"""
        }.andExpect { jsonPath("$.code") { value("staff.cannot_deactivate_self") } }
        mockMvc.post("/api/v1/staff/${other.id}/deactivate") {
            with(asStaff(admin)); contentType = MediaType.APPLICATION_JSON; content = """{"version":0}"""
        }.andExpect { jsonPath("$.active") { value(false) } }
    }

    @Test
    fun `license limits the number of active staff, the vendor account does not count`() {
        resetState()
        pairClinic(TestLicenses.payload(clinicId, maxUsers = 2))
        createStaff()
        createStaff("vendor.support", RoleTemplate.VENDOR_SUPPORT)

        createVia(login = "second", template = RoleTemplate.DOCTOR).andExpect { status { isCreated() } }
        createVia(login = "third", template = RoleTemplate.DOCTOR).andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.code") { value("license.max_users_reached") }
            jsonPath("$.params.maxUsers") { value(2) }
        }
    }

    @Test
    fun `changing a role's permissions ends the sessions of its staff`() {
        val admin = createStaff()
        createStaff("doctor", RoleTemplate.DOCTOR)
        val doctorSession = login("doctor")
        val doctorRole = roleRepository.findByTemplate(RoleTemplate.DOCTOR)!!

        mockMvc.put("/api/v1/roles/${doctorRole.id}") {
            with(asStaff(admin)); contentType = MediaType.APPLICATION_JSON
            content = """{"permissions":["PATIENT_VIEW"],"version":0}"""
        }.andExpect { jsonPath("$.permissions.length()") { value(1) } }

        mockMvc.get("/api/v1/auth/me") { with(doctorSession.apply()) }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `custom roles can be created, are unique by name and deleted only when unused`() {
        val admin = createStaff()
        val response = mockMvc.post("/api/v1/roles") {
            with(asStaff(admin)); contentType = MediaType.APPLICATION_JSON
            content = """{"name":"UZI shifokori","permissions":["PATIENT_VIEW","CONSULTATION_WRITE"]}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val id = body(response).path("id").asString()

        mockMvc.post("/api/v1/roles") {
            with(asStaff(admin)); contentType = MediaType.APPLICATION_JSON
            content = """{"name":"uzi SHIFOKORI","permissions":[]}"""
        }.andExpect { jsonPath("$.code") { value("role.name_taken") } }
        mockMvc.post("/api/v1/roles") {
            with(asStaff(admin)); contentType = MediaType.APPLICATION_JSON
            content = """{"name":"X","permissions":["FLY"]}"""
        }.andExpect { jsonPath("$.fieldErrors[0].code") { value("validation.unknown_permissions") } }

        createStaff("uzi", roleId = UUID.fromString(id))
        mockMvc.delete("/api/v1/roles/$id?version=0") { with(asStaff(admin)) }.andExpect { jsonPath("$.code") { value("role.in_use") } }
    }

    @Test
    fun `signature accepts png and jpeg only`() {
        val admin = createStaff()
        val doctor = createStaff("doctor", RoleTemplate.DOCTOR)
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 0, 0)

        mockMvc.multipart("/api/v1/staff/${doctor.id}/signature") {
            file(MockMultipartFile("file", "sign.png", "image/png", png)); with(asStaff(admin))
            with { it.method = "PUT"; it }
        }.andExpect { status { isNoContent() } }
        mockMvc.get("/api/v1/staff/${doctor.id}/signature") { with(asStaff(admin)) }.andExpect {
            status { isOk() }
            content { contentType("image/png") }
        }
        mockMvc.multipart("/api/v1/staff/${doctor.id}/signature") {
            file(MockMultipartFile("file", "sign.png", "image/png", "<svg/>".toByteArray())); with(asStaff(admin))
            with { it.method = "PUT"; it }
        }.andExpect { jsonPath("$.fieldErrors[0].code") { value("validation.image_type") } }
        assertThat(jdbc.sql("select count(*) from audit_log where action = 'staff.signature_changed'").query(Long::class.java).single()).isEqualTo(1)
    }

    @Test
    fun `login, temporary password and logout work end to end`() {
        createStaff("fresh", RoleTemplate.DOCTOR, mustChangePassword = true)
        val session = login("fresh")

        mockMvc.get("/api/v1/roles") { with(session.apply()) }.andExpect { jsonPath("$.code") { value("auth.password_change_required") } }
        mockMvc.post("/api/v1/auth/password") {
            with(session.apply()); contentType = MediaType.APPLICATION_JSON
            content = """{"currentPassword":"$PASSWORD","newPassword":"Doctor123"}"""
        }.andExpect { jsonPath("$.mustChangePassword") { value(false) } }
        mockMvc.post("/api/v1/auth/logout") { with(session.apply()) }.andExpect { status { isNoContent() } }
        mockMvc.get("/api/v1/auth/me") { with(session.apply()) }.andExpect { status { isUnauthorized() } }
    }
}
