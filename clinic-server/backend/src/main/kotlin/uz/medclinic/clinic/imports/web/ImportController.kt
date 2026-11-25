package uz.medclinic.clinic.imports.web

import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import uz.medclinic.clinic.imports.application.ImportResult
import uz.medclinic.clinic.imports.application.ImportService
import uz.medclinic.clinic.imports.application.ImportTexts
import uz.medclinic.clinic.imports.domain.ImportKind
import uz.medclinic.clinic.imports.domain.ImportRow
import uz.medclinic.clinic.imports.domain.ImportedItem
import uz.medclinic.platform.domain.ValidationException
import java.time.Instant
import java.util.UUID

data class ImportPreviewResponse(
    val batchId: UUID,
    val kind: ImportKind,
    val columns: List<String>,
    val rows: List<ImportRow>,
    val validRows: Int,
    val invalidRows: Int,
    val expiresAt: Instant,
)

data class ImportResultResponse(val imported: List<ImportedItem>, val skipped: List<ImportRow>) {
    constructor(r: ImportResult) : this(r.imported, r.skipped)
}

/** Staff import requires both DATA_IMPORT and STAFF_MANAGE (it creates accounts). */
@RestController
@RequestMapping("/api/v1/imports")
@PreAuthorize("hasAuthority('DATA_IMPORT') and hasAuthority('STAFF_MANAGE')")
class ImportController(private val imports: ImportService) {
    @GetMapping("/{kind}/template")
    fun template(@PathVariable kind: ImportKind, @RequestParam(defaultValue = "uz-Latn") lang: String): ResponseEntity<ByteArray> {
        val language = lang.takeIf { it in ImportTexts.LANGUAGES } ?: "uz-Latn"
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(XLSX))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("${kind.name.lowercase()}-template.xlsx").build().toString())
            .body(imports.template(kind, language))
    }

    @PostMapping("/{kind}/preview", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun preview(@PathVariable kind: ImportKind, @RequestPart("file") file: MultipartFile): ImportPreviewResponse {
        if (file.isEmpty) throw ValidationException("file", "validation.import_empty")
        val preview = imports.preview(kind, file.bytes, file.originalFilename)
        val columns = preview.rows.firstOrNull()?.values?.keys?.toList().orEmpty()
        return ImportPreviewResponse(preview.batchId, preview.kind, columns, preview.rows, preview.validRows, preview.invalidRows, preview.expiresAt)
    }

    @PostMapping("/{batchId}/commit")
    fun commit(@PathVariable batchId: UUID) = ImportResultResponse(imports.commit(batchId))

    private companion object {
        const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }
}
