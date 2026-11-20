package uz.medclinic.vendor.tenancy

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import uz.medclinic.vendor.identity.domain.StaffAccount
import uz.medclinic.vendor.identity.domain.VendorRole
import uz.medclinic.vendor.support.IntegrationTest
import java.util.UUID

class TenancyApiTest : IntegrationTest() {
    private fun createClinic(staff: StaffAccount, name: String = "Shifo Med", phone: String = "+998 90 123 45 67"): UUID {
        val response = mockMvc.post("/api/v1/clinics") {
            with(asStaff(staff)); contentType = MediaType.APPLICATION_JSON
            content = """{"name":"$name","ownerName":"Vali Aliyev","phone":"$phone","region":"SAMARKAND","legalName":"MChJ $name"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return UUID.fromString(body(response).path("id").asString())
    }

    private fun createBranch(staff: StaffAccount, clinicId: UUID, name: String) = mockMvc.post("/api/v1/clinics/$clinicId/branches") {
        with(asStaff(staff)); contentType = MediaType.APPLICATION_JSON
        content = """{"name":"$name","phone":"901112233"}"""
    }

    private fun setStatus(staff: StaffAccount, path: String, status: String, version: Int) = mockMvc.post("$path/status") {
        with(asStaff(staff)); contentType = MediaType.APPLICATION_JSON
        content = """{"status":"$status","version":$version}"""
    }

    @Test
    fun `support creates clinics with sequential codes and branches with derived codes`() {
        val support = createStaff("support", role = VendorRole.SUPPORT)

        val first = createClinic(support, "Shifo Med")
        createClinic(support, "Sog'lom Hayot")

        mockMvc.get("/api/v1/clinics/$first") { with(asStaff(support)) }.andExpect {
            jsonPath("$.code") { value("CL-0001") }
            jsonPath("$.phone") { value("+998901234567") }
            jsonPath("$.status") { value("ACTIVE") }
        }
        createBranch(support, first, "Markaziy").andExpect { jsonPath("$.code") { value("CL-0001-01") } }
        createBranch(support, first, "Shimoliy").andExpect { jsonPath("$.code") { value("CL-0001-02") } }
        createBranch(support, first, "markaziy").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("branch.name_taken") }
        }
        mockMvc.get("/api/v1/clinics?q=shifo") { with(asStaff(support)) }.andExpect {
            jsonPath("$.totalItems") { value(1) }
            jsonPath("$.items[0].branchCount") { value(2) }
        }
        mockMvc.get("/api/v1/clinics?q=4567") { with(asStaff(support)) }.andExpect { jsonPath("$.totalItems") { value(2) } }
        mockMvc.get("/api/v1/clinics?q=50%25") { with(asStaff(support)) }.andExpect { jsonPath("$.totalItems") { value(0) } }
    }

    @Test
    fun `invalid clinic input returns all field errors`() {
        val owner = createStaff("owner")

        mockMvc.post("/api/v1/clinics") {
            with(asStaff(owner)); contentType = MediaType.APPLICATION_JSON
            content = """{"name":" ","phone":"12345"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.fieldErrors.length()") { value(4) }
        }
    }

    @Test
    fun `support cannot change status, owner follows the status machine with optimistic locking`() {
        val owner = createStaff("owner")
        val support = createStaff("support", role = VendorRole.SUPPORT)
        val clinicId = createClinic(owner)
        val path = "/api/v1/clinics/$clinicId"

        setStatus(support, path, "SUSPENDED", 0).andExpect { status { isForbidden() } }
        setStatus(owner, path, "SUSPENDED", 0).andExpect { jsonPath("$.status") { value("SUSPENDED") } }
        setStatus(owner, path, "ARCHIVED", 0).andExpect { status { isConflict() } }
        setStatus(owner, path, "ARCHIVED", 1).andExpect { jsonPath("$.version") { value(2) } }

        mockMvc.put(path) {
            with(asStaff(owner)); contentType = MediaType.APPLICATION_JSON
            content = """{"name":"X","ownerName":"Y","phone":"901234567","region":"NAVOI","version":2}"""
        }.andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.code") { value("clinic.archived") }
        }
        setStatus(owner, path, "SUSPENDED", 2).andExpect {
            jsonPath("$.code") { value("tenant.invalid_status_transition") }
            jsonPath("$.params.from") { value("ARCHIVED") }
        }
    }

    @Test
    fun `only archived clinics can be deleted, and deletion hides the clinic and its branches`() {
        val owner = createStaff("owner")
        val clinicId = createClinic(owner)
        val branchId = body(createBranch(owner, clinicId, "Markaziy").andReturn().response.contentAsString).path("id").asString()

        mockMvc.delete("/api/v1/clinics/$clinicId?version=0") { with(asStaff(owner)) }.andExpect {
            jsonPath("$.code") { value("clinic.delete_requires_archived") }
        }
        setStatus(owner, "/api/v1/clinics/$clinicId", "ARCHIVED", 0)
        mockMvc.delete("/api/v1/clinics/$clinicId?version=1") { with(asStaff(owner)) }.andExpect { status { isNoContent() } }

        mockMvc.get("/api/v1/clinics/$clinicId") { with(asStaff(owner)) }.andExpect { status { isNotFound() } }
        mockMvc.get("/api/v1/branches/$branchId") { with(asStaff(owner)) }.andExpect { status { isNotFound() } }
        mockMvc.get("/api/v1/clinics") { with(asStaff(owner)) }.andExpect { jsonPath("$.totalItems") { value(0) } }
        val actions = jdbc.sql("select action from audit_log order by occurred_at, id").query(String::class.java).list()
        assertThat(actions).containsExactly("clinic.created", "branch.created", "clinic.status_changed", "clinic.deleted")
    }

    @Test
    fun `branches of archived clinics cannot be created or edited`() {
        val owner = createStaff("owner")
        val clinicId = createClinic(owner)
        setStatus(owner, "/api/v1/clinics/$clinicId", "ARCHIVED", 0)

        createBranch(owner, clinicId, "New").andExpect { jsonPath("$.code") { value("clinic.archived") } }
    }
}
