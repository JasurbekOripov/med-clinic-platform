package uz.medclinic.vendor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import uz.medclinic.vendor.support.IntegrationTest
import java.nio.file.Files
import java.nio.file.Path

/**
 * Writes the OpenAPI contract to `docs/openapi/vendor-platform.json` (committed). The web console generates its
 * TypeScript types from it (`pnpm gen:api`), so API changes show up as reviewable diffs.
 */
class OpenApiExportTest : IntegrationTest() {
    @Test
    fun `exports the openapi contract`() {
        val spec = mockMvc.get("/v3/api-docs").andExpect { status { isOk() } }.andReturn().response.contentAsString
        val tree = json.readTree(spec)
        assertThat(tree.path("paths").has("/api/v1/clinics")).isTrue()
        assertThat(tree.path("paths").has("/api/device/v1/heartbeats")).isTrue()

        val target = Path.of(System.getProperty("user.dir")).resolve("../../docs/openapi/vendor-platform.json").normalize()
        Files.createDirectories(target.parent)
        Files.writeString(target, json.writerWithDefaultPrettyPrinter().writeValueAsString(tree) + "\n")
    }
}
