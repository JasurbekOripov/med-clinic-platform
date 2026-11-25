package uz.medclinic.clinic.imports.application

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.clinic.audit.api.AuditEvent
import uz.medclinic.clinic.audit.api.AuditRecorder
import uz.medclinic.clinic.imports.domain.ImportKind
import uz.medclinic.clinic.imports.domain.ImportRow
import uz.medclinic.clinic.imports.domain.ImportedItem
import uz.medclinic.clinic.imports.domain.Importer
import uz.medclinic.clinic.imports.infrastructure.ImportBatch
import uz.medclinic.clinic.imports.infrastructure.JdbcImportBatchRepository
import uz.medclinic.clinic.imports.infrastructure.SpreadsheetCodec
import uz.medclinic.common.Ids
import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.domain.NotFoundException
import uz.medclinic.platform.security.CurrentActor
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class ImportPreview(val batchId: UUID, val kind: ImportKind, val rows: List<ImportRow>, val expiresAt: Instant) {
    val validRows: Int get() = rows.count { it.valid }
    val invalidRows: Int get() = rows.size - validRows
}

data class ImportResult(val imported: List<ImportedItem>, val skipped: List<ImportRow>)

/**
 * Template → upload → preview (nothing saved) → confirm. Only valid rows are imported; rows are validated again at
 * confirmation because the data may have changed in the meantime.
 */
@Service
class ImportService(
    importers: List<Importer>,
    private val codec: SpreadsheetCodec,
    private val batches: JdbcImportBatchRepository,
    private val audit: AuditRecorder,
    private val clock: Clock,
) {
    private val importers: Map<ImportKind, Importer> = importers.associateBy { it.kind }

    fun template(kind: ImportKind, lang: String): ByteArray = when (kind) {
        ImportKind.STAFF -> codec.template(ImportTexts.staffHeaders(lang), ImportTexts.staffExample(lang))
    }

    @Transactional
    fun preview(kind: ImportKind, bytes: ByteArray, fileName: String?): ImportPreview {
        val importer = importer(kind)
        val headers = when (kind) {
            ImportKind.STAFF -> ImportTexts.acceptedStaffHeaders()
        }
        val rows = importer.validate(codec.read(bytes, importer.columns, headers, MAX_ROWS))
        val now = clock.instant()
        val batch = ImportBatch(Ids.newId(), kind, rows, fileName?.take(255), CurrentActor.staffId(), now, now.plus(TTL), null)
        batches.insert(batch)
        return ImportPreview(batch.id, kind, rows, batch.expiresAt)
    }

    @Transactional
    fun commit(batchId: UUID): ImportResult {
        val batch = batches.findForUpdate(batchId) ?: throw NotFoundException("import", batchId)
        // A preview belongs to the person who uploaded it.
        if (batch.createdBy != CurrentActor.staffId()) throw AccessDeniedException("import batch of another user")
        if (batch.committedAt != null) throw DomainException("import.already_committed", DomainException.Kind.CONFLICT)
        if (clock.instant().isAfter(batch.expiresAt)) throw DomainException("import.expired")

        val importer = importer(batch.kind)
        val revalidated = importer.validate(batch.rows.map { it.copy(errors = emptyList(), warnings = emptyList()) })
        val (valid, invalid) = revalidated.partition { it.valid }
        if (valid.isEmpty()) throw DomainException("import.nothing_to_import")
        val imported = importer.commit(valid)
        batches.markCommitted(batchId, clock.instant())
        audit.record(
            AuditEvent("import.committed", "import", batchId, after = mapOf("kind" to batch.kind, "imported" to imported.size, "skipped" to invalid.size, "file" to batch.fileName)),
        )
        return ImportResult(imported, invalid)
    }

    @Scheduled(cron = "0 23 * * * *")
    @Transactional
    fun purgeExpired() {
        batches.deleteExpired(clock.instant().minus(Duration.ofDays(1)))
    }

    private fun importer(kind: ImportKind) = importers[kind] ?: throw NotFoundException("importer", kind)

    private companion object {
        const val MAX_ROWS = 2000
        val TTL: Duration = Duration.ofMinutes(30)
    }
}
