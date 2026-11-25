package uz.medclinic.clinic.imports

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import uz.medclinic.clinic.identity.domain.RoleTemplate
import uz.medclinic.clinic.identity.domain.StaffMember
import uz.medclinic.clinic.support.IntegrationTest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Duration

class StaffImportTest : IntegrationTest() {
    private lateinit var admin: StaffMember

    @BeforeEach
    fun setUp() {
        pairClinic()
        admin = createStaff()
    }

    private fun xlsx(header: List<String>, vararg rows: List<Any?>): ByteArray = XSSFWorkbook().use { wb ->
        val sheet = wb.createSheet()
        (listOf(header) + rows).forEachIndexed { r, values ->
            val row = sheet.createRow(r)
            values.forEachIndexed { c, v ->
                when (v) {
                    null -> Unit
                    is Number -> row.createCell(c).setCellValue(v.toDouble())
                    else -> row.createCell(c).setCellValue(v.toString())
                }
            }
        }
        ByteArrayOutputStream().also(wb::write).toByteArray()
    }

    private val ruHeader = listOf("Ф.И.О.", "Логин", "Роль", "Должность", "Телефон")

    private fun preview(bytes: ByteArray, as_: StaffMember = admin) = mockMvc.multipart("/api/v1/imports/STAFF/preview") {
        file(MockMultipartFile("file", "staff.xlsx", "application/octet-stream", bytes)); with(asStaff(as_))
    }

    @Test
    fun `template comes in the requested language`() {
        val bytes = mockMvc.get("/api/v1/imports/STAFF/template?lang=uz-Cyrl") { with(asStaff(admin)) }
            .andExpect { status { isOk() } }.andReturn().response.contentAsByteArray
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { wb ->
            assertThat(wb.getSheetAt(0).getRow(0).getCell(0).stringCellValue).isEqualTo("Ф.И.Ш.")
            assertThat(wb.getSheetAt(0).getRow(1).getCell(2).stringCellValue).isEqualTo("Қабулхона")
        }
    }

    @Test
    fun `preview reports every problem, confirm imports only valid rows with one-time passwords`() {
        createStaff("taken", RoleTemplate.DOCTOR)
        val file = xlsx(
            ruHeader,
            listOf("Каримова Дилноза", "dilnoza", "Регистратура", "Регистратор", 901234567L),
            listOf("Aliyev Vali", "vali", "shifokor", null, "+998 93 111 22 33"),
            listOf("Duplicate", "DILNOZA", "DOCTOR", null, null),
            listOf("Taken Login", "taken", "LAB", null, null),
            listOf("Bad Phone", "badphone", "NURSE", null, "12345"),
            listOf("No Role", "norole", "Astronaut", null, null),
            listOf(null, null, null, null, null),
            listOf("Vendor Role", "vendorrole", "VENDOR_SUPPORT", null, null),
        )

        val response = preview(file).andExpect {
            status { isOk() }
            jsonPath("$.validRows") { value(2) }
            jsonPath("$.invalidRows") { value(5) }
            jsonPath("$.rows[2].errors[0].code") { value("import.duplicate_in_file") }
            jsonPath("$.rows[3].errors[0].code") { value("staff.login_taken") }
            jsonPath("$.rows[4].errors[0].code") { value("validation.phone") }
            jsonPath("$.rows[5].errors[0].code") { value("validation.unknown_role") }
            jsonPath("$.rows[6].errors[0].code") { value("validation.unknown_role") }
        }.andReturn().response.contentAsString
        assertThat(staffRepository.findByLogin("dilnoza")).isNull()

        val batchId = body(response).path("batchId").asString()
        mockMvc.post("/api/v1/imports/$batchId/commit") { with(asStaff(admin)) }.andExpect {
            status { isOk() }
            jsonPath("$.imported.length()") { value(2) }
            jsonPath("$.imported[0].details.login") { value("dilnoza") }
            jsonPath("$.imported[0].details.temporaryPassword") { exists() }
            jsonPath("$.skipped.length()") { value(5) }
        }
        assertThat(staffRepository.findByLogin("dilnoza")!!.profile.phone?.e164).isEqualTo("+998901234567")
        assertThat(staffRepository.findByLogin("vali")!!.mustChangePassword).isTrue()

        mockMvc.post("/api/v1/imports/$batchId/commit") { with(asStaff(admin)) }.andExpect { jsonPath("$.code") { value("import.already_committed") } }
    }

    @Test
    fun `a file not made from the template is rejected`() {
        preview(xlsx(listOf("Name", "Email"), listOf("a", "b"))).andExpect {
            status { isBadRequest() }
            jsonPath("$.fieldErrors[0].code") { value("validation.import_wrong_template") }
        }
        preview("not an excel file".toByteArray()).andExpect { jsonPath("$.fieldErrors[0].code") { value("validation.import_not_xlsx") } }
    }

    @Test
    fun `only the uploader can confirm, and only before the preview expires`() {
        val other = createStaff("admin2")
        val batchId = body(preview(xlsx(ruHeader, listOf("A B", "abc", "DOCTOR", null, null))).andReturn().response.contentAsString)
            .path("batchId").asString()

        mockMvc.post("/api/v1/imports/$batchId/commit") { with(asStaff(other)) }.andExpect { status { isForbidden() } }
        clock.advance(Duration.ofMinutes(31))
        mockMvc.post("/api/v1/imports/$batchId/commit") { with(asStaff(admin)) }.andExpect { jsonPath("$.code") { value("import.expired") } }
    }

    @Test
    fun `staff without import permission cannot import`() {
        val doctor = createStaff("doctor", RoleTemplate.DOCTOR)
        preview(xlsx(ruHeader, listOf("A B", "abc", "DOCTOR", null, null)), doctor).andExpect { status { isForbidden() } }
    }
}
