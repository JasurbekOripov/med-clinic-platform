package uz.medclinic.vendor

import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import uz.medclinic.vendor.support.IntegrationTest

class ApplicationSmokeTest : IntegrationTest() {
    @Test
    fun `health endpoint is public and up`() {
        mockMvc.get("/actuator/health").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("UP") }
        }
    }

    @Test
    fun `unknown api path returns problem json`() {
        mockMvc.get("/api/v1/does-not-exist").andExpect {
            status { is4xxClientError() }
        }
    }
}
