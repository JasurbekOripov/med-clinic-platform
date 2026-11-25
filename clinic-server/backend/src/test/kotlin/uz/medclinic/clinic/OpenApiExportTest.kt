package uz.medclinic.clinic

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import uz.medclinic.clinic.support.IntegrationTest
import java.nio.file.Files
import java.nio.file.Path

/** Writes `docs/openapi/clinic-server.json`; the clinic web app generates its types from it (`pnpm gen:api`). */
class OpenApiExportTest : IntegrationTest() {
    @Test
    fun `exports the openapi contract`() {
        val spec = mockMvc.get("/v3/api-docs").andExpect { status { isOk() } }.andReturn().response.contentAsString
        val tree = json.readTree(spec)
        assertThat(tree.path("paths").has("/api/v1/staff")).isTrue()
        val target = Path.of(System.getProperty("user.dir")).resolve("../../docs/openapi/clinic-server.json").normalize()
        Files.writeString(target, json.writerWithDefaultPrettyPrinter().writeValueAsString(tree) + "\n")
    }
}
