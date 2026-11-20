package uz.medclinic.vendor

import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import uz.medclinic.vendor.support.IntegrationTest

/** API and asset paths must never be answered with the console page (works with or without a built console). */
class SpaRoutingTest : IntegrationTest() {
    @Test
    fun `missing assets are 404 and unknown api paths stay protected api errors`() {
        mockMvc.get("/assets/missing-file.js").andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith("application/problem+json") }
        }
        mockMvc.get("/api/v1/nothing-here").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("auth.unauthenticated") }
        }
        val owner = createStaff("owner")
        mockMvc.get("/api/v1/nothing-here") { with(asStaff(owner)) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("common.not_found") }
        }
    }
}
