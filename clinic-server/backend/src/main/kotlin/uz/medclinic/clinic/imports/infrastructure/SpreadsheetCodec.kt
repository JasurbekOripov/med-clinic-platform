package uz.medclinic.clinic.imports.infrastructure

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Component
import uz.medclinic.clinic.imports.domain.ImportColumn
import uz.medclinic.clinic.imports.domain.ImportRow
import uz.medclinic.platform.domain.ValidationException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Reads and writes the `.xlsx` files. Columns are matched by position (the template's order); the header row must
 * be one of the template headers in any supported language, so a wrong or shuffled file is rejected early.
 */
@Component
class SpreadsheetCodec {
    private val formatter = DataFormatter()

    fun template(headers: List<String>, example: List<String>): ByteArray = XSSFWorkbook().use { wb ->
        val sheet = wb.createSheet("Import")
        val bold = wb.createFont().apply { bold = true }
        val headerStyle = wb.createCellStyle().apply {
            setFont(bold)
            fillForegroundColor = IndexedColors.LIGHT_GREEN.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }
        val header = sheet.createRow(0)
        headers.forEachIndexed { i, text -> header.createCell(i).apply { setCellValue(text); cellStyle = headerStyle } }
        val sample = sheet.createRow(1)
        example.forEachIndexed { i, text -> sample.createCell(i).setCellValue(text) }
        headers.indices.forEach { sheet.setColumnWidth(it, 26 * 256) }
        sheet.createFreezePane(0, 1)
        ByteArrayOutputStream().also(wb::write).toByteArray()
    }

    /** [acceptedHeaders] holds, per column, the header text in every supported language. */
    fun read(bytes: ByteArray, columns: List<ImportColumn>, acceptedHeaders: List<Set<String>>, maxRows: Int): List<ImportRow> {
        val workbook = try {
            XSSFWorkbook(ByteArrayInputStream(bytes))
        } catch (_: Exception) {
            throw ValidationException("file", "validation.import_not_xlsx")
        }
        workbook.use { wb ->
            val sheet = wb.getSheetAt(0) ?: throw ValidationException("file", "validation.import_empty")
            val header = sheet.getRow(sheet.firstRowNum) ?: throw ValidationException("file", "validation.import_empty")
            columns.indices.forEach { i ->
                val text = normalise(text(header.getCell(i)))
                if (text == null || acceptedHeaders[i].none { normalise(it) == text }) {
                    throw ValidationException("file", "validation.import_wrong_template", mapOf("column" to i + 1))
                }
            }
            val rows = mutableListOf<ImportRow>()
            for (r in sheet.firstRowNum + 1..sheet.lastRowNum) {
                val row = sheet.getRow(r) ?: continue
                val values = columns.mapIndexed { i, c -> c.key to text(row.getCell(i)) }.toMap()
                if (values.values.all { it == null }) continue
                if (rows.size >= maxRows) throw ValidationException("file", "validation.import_too_many_rows", mapOf("max" to maxRows))
                rows += ImportRow(rowNumber = r + 1, values = values)
            }
            if (rows.isEmpty()) throw ValidationException("file", "validation.import_empty")
            return rows
        }
    }

    private fun text(cell: Cell?): String? {
        if (cell == null || cell.cellType == CellType.BLANK) return null
        // Phone numbers typed as numbers must not become "9.01234567E8".
        val raw = if (cell.cellType == CellType.NUMERIC) formatter.formatCellValue(cell) else cell.toString()
        return raw.trim().takeIf { it.isNotEmpty() }
    }

    private fun normalise(text: String?): String? = text?.trim()?.lowercase()?.replace(Regex("\\s+"), " ")
}
