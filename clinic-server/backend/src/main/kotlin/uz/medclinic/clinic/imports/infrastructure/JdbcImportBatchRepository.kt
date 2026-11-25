package uz.medclinic.clinic.imports.infrastructure

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uz.medclinic.clinic.imports.domain.ImportKind
import uz.medclinic.clinic.imports.domain.ImportRow
import uz.medclinic.clinic.shared.ClinicContext
import uz.medclinic.platform.persistence.instant
import uz.medclinic.platform.persistence.instantOrNull
import uz.medclinic.platform.persistence.toDb
import uz.medclinic.platform.persistence.uuid
import java.time.Instant
import java.util.UUID

data class ImportBatch(
    val id: UUID,
    val kind: ImportKind,
    val rows: List<ImportRow>,
    val fileName: String?,
    val createdBy: UUID,
    val createdAt: Instant,
    val expiresAt: Instant,
    val committedAt: Instant?,
)

@Repository
class JdbcImportBatchRepository(private val jdbc: JdbcClient, private val mapper: JsonMapper, private val clinic: ClinicContext) {
    fun insert(batch: ImportBatch) {
        val identity = clinic.current()
        jdbc.sql(
            """
            insert into import_batch (id, clinic_id, branch_id, kind, rows, file_name, created_by, created_at, expires_at)
            values (:id, :clinicId, :branchId, :kind, cast(:rows as jsonb), :fileName, :createdBy, :createdAt, :expiresAt)
            """,
        )
            .param("id", batch.id).param("clinicId", identity.clinicId).param("branchId", identity.branchId)
            .param("kind", batch.kind.name).param("rows", mapper.writeValueAsString(batch.rows)).param("fileName", batch.fileName)
            .param("createdBy", batch.createdBy).param("createdAt", batch.createdAt.toDb()).param("expiresAt", batch.expiresAt.toDb())
            .update()
    }

    /** Locks the batch so a double click cannot commit it twice. */
    fun findForUpdate(id: UUID): ImportBatch? =
        jdbc.sql("select * from import_batch where id = :id for update").param("id", id).query { rs, _ ->
            ImportBatch(
                rs.uuid("id"), ImportKind.valueOf(rs.getString("kind")), mapper.readValue<List<ImportRow>>(rs.getString("rows")),
                rs.getString("file_name"), rs.uuid("created_by"), rs.instant("created_at"), rs.instant("expires_at"),
                rs.instantOrNull("committed_at"),
            )
        }.optional().orElse(null)

    fun markCommitted(id: UUID, at: Instant) {
        jdbc.sql("update import_batch set committed_at = :at where id = :id").param("id", id).param("at", at.toDb()).update()
    }

    fun deleteExpired(before: Instant): Int =
        jdbc.sql("delete from import_batch where expires_at < :before").param("before", before.toDb()).update()
}
