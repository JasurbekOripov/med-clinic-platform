package uz.medclinic.vendor.identity

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import uz.medclinic.vendor.identity.domain.VendorRole
import uz.medclinic.vendor.support.IntegrationTest

class StaffApiTest : IntegrationTest() {
    @Test
    fun `owner creates support staff who must change the temporary password`() {
        val owner = createStaff("owner")

        val response = mockMvc.post("/api/v1/staff") {
            with(asStaff(owner))
            contentType = MediaType.APPLICATION_JSON
            content = """{"login":" Aziz.Support ","fullName":"  Aziz   Karimov ","role":"SUPPORT"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.staff.login") { value("aziz.support") }
            jsonPath("$.staff.fullName") { value("Aziz Karimov") }
            jsonPath("$.staff.mustChangePassword") { value(true) }
            jsonPath("$.staff.passwordHash") { doesNotExist() }
        }.andReturn().response.contentAsString

        val temporaryPassword = body(response).path("temporaryPassword").asString()
        mockMvc.get("/api/v1/auth/me") { with(login("aziz.support", temporaryPassword).apply()) }.andExpect {
            jsonPath("$.mustChangePassword") { value(true) }
            jsonPath("$.permissions.length()") { value(5) }
        }
        mockMvc.get("/api/v1/audit?action=staff.created") { with(asStaff(owner)) }.andExpect {
            jsonPath("$.totalItems") { value(1) }
            jsonPath("$.items[0].after.role") { value("SUPPORT") }
            jsonPath("$.items[0].after.passwordHash") { doesNotExist() }
        }
    }

    @Test
    fun `duplicate login and invalid input are rejected`() {
        val owner = createStaff("owner")

        mockMvc.post("/api/v1/staff") {
            with(asStaff(owner))
            contentType = MediaType.APPLICATION_JSON
            content = """{"login":"OWNER","fullName":"Other","role":"SUPPORT"}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("staff.login_taken") }
        }
        mockMvc.post("/api/v1/staff") {
            with(asStaff(owner))
            contentType = MediaType.APPLICATION_JSON
            content = """{"login":"a b","fullName":"","role":"SUPPORT"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("validation.failed") }
            jsonPath("$.fieldErrors[0].field") { value("fullName") }
        }
    }

    @Test
    fun `support cannot manage staff`() {
        val support = createStaff("support", role = VendorRole.SUPPORT)

        mockMvc.get("/api/v1/staff") { with(asStaff(support)) }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("auth.forbidden") }
        }
    }

    @Test
    fun `the last active owner cannot be demoted or deactivated, and nobody deactivates themselves`() {
        val owner = createStaff("owner")
        val other = createStaff("other")

        mockMvc.post("/api/v1/staff/${owner.id}/deactivate") {
            with(asStaff(owner)); contentType = MediaType.APPLICATION_JSON; content = """{"version":0}"""
        }.andExpect { jsonPath("$.code") { value("staff.cannot_deactivate_self") } }

        mockMvc.post("/api/v1/staff/${other.id}/deactivate") {
            with(asStaff(owner)); contentType = MediaType.APPLICATION_JSON; content = """{"version":0}"""
        }.andExpect { status { isOk() } }

        mockMvc.put("/api/v1/staff/${owner.id}") {
            with(asStaff(owner)); contentType = MediaType.APPLICATION_JSON
            content = """{"fullName":"Owner","role":"SUPPORT","version":0}"""
        }.andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.code") { value("staff.last_owner") }
        }
    }

    @Test
    fun `stale version is a conflict`() {
        val owner = createStaff("owner")
        val support = createStaff("support", role = VendorRole.SUPPORT)

        mockMvc.put("/api/v1/staff/${support.id}") {
            with(asStaff(owner)); contentType = MediaType.APPLICATION_JSON
            content = """{"fullName":"Renamed","role":"SUPPORT","version":0}"""
        }.andExpect { jsonPath("$.version") { value(1) } }

        mockMvc.put("/api/v1/staff/${support.id}") {
            with(asStaff(owner)); contentType = MediaType.APPLICATION_JSON
            content = """{"fullName":"Again","role":"SUPPORT","version":0}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("common.version_conflict") }
        }
    }

    @Test
    fun `password reset issues a new temporary password and invalidates the old one`() {
        val owner = createStaff("owner")
        val support = createStaff("support", role = VendorRole.SUPPORT)

        val response = mockMvc.post("/api/v1/staff/${support.id}/reset-password") {
            with(asStaff(owner)); contentType = MediaType.APPLICATION_JSON; content = """{"version":0}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        login("support", body(response).path("temporaryPassword").asString())
    }
}
