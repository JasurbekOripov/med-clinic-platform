package uz.medclinic.clinic.conclusions.infrastructure

import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import uz.medclinic.clinic.conclusions.domain.Conclusion
import uz.medclinic.clinic.conclusions.domain.ConclusionNote
import uz.medclinic.clinic.conclusions.domain.ConclusionRepository
import uz.medclinic.clinic.conclusions.domain.ConclusionTemplate
import uz.medclinic.clinic.conclusions.domain.ConclusionTemplateRepository
import uz.medclinic.clinic.conclusions.domain.ConclusionText
import uz.medclinic.clinic.shared.ClinicContext
import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.persistence.instant
import uz.medclinic.platform.persistence.instantOrNull
import uz.medclinic.platform.persistence.requireVersionMatched
import uz.medclinic.platform.persistence.toDb
import uz.medclinic.platform.persistence.toDbOrNull
import uz.medclinic.platform.persistence.uuid
import java.sql.ResultSet
import java.util.UUID

@Repository
class JdbcConclusionRepository(private val jdbc: JdbcClient, private val clinic: ClinicContext) : ConclusionRepository {
    override fun insert(conclusion: Conclusion) {
        val identity = clinic.current()
        try {
            jdbc.sql(
                """
                insert into conclusion (id, clinic_id, branch_id, patient_id, visit_id, visit_item_id, author_id, author_name, title, body,
                                        created_at, updated_at, version)
                values (:id, :clinicId, :branchId, :patientId, :visitId, :itemId, :authorId, :authorName, :title, :body, :createdAt, :updatedAt, 0)
                """,
            )
                .param("id", conclusion.id).param("clinicId", identity.clinicId).param("branchId", identity.branchId)
                .param("patientId", conclusion.patientId).param("visitId", conclusion.visitId).param("itemId", conclusion.visitItemId)
                .param("authorId", conclusion.authorId).param("authorName", conclusion.authorName)
                .param("title", conclusion.text.title).param("body", conclusion.text.body)
                .param("createdAt", conclusion.createdAt.toDb()).param("updatedAt", conclusion.updatedAt.toDb()).update()
        } catch (_: DuplicateKeyException) {
            throw DomainException("conclusion.already_written", DomainException.Kind.CONFLICT)
        }
    }

    override fun findById(id: UUID): Conclusion? = withNotes(jdbc.sql("select * from conclusion where id = :id").param("id", id).query(::map).list()).firstOrNull()

    override fun findByPatient(patientId: UUID): List<Conclusion> =
        withNotes(jdbc.sql("select * from conclusion where patient_id = :p order by created_at desc, id desc").param("p", patientId).query(::map).list())

    override fun update(conclusion: Conclusion): Conclusion {
        val rows = jdbc.sql(
            "update conclusion set title = :title, body = :body, updated_at = :updatedAt, version = version + 1 where id = :id and version = :version",
        ).param("id", conclusion.id).param("title", conclusion.text.title).param("body", conclusion.text.body)
            .param("updatedAt", conclusion.updatedAt.toDb()).param("version", conclusion.version).update()
        requireVersionMatched(rows, "conclusion", conclusion.id)
        return conclusion.copy(version = conclusion.version + 1)
    }

    override fun addNote(conclusionId: UUID, note: ConclusionNote) {
        jdbc.sql(
            "insert into conclusion_note (id, conclusion_id, author_id, author_name, text, created_at) values (:id, :c, :authorId, :authorName, :text, :createdAt)",
        ).param("id", note.id).param("c", conclusionId).param("authorId", note.authorId).param("authorName", note.authorName)
            .param("text", note.text).param("createdAt", note.createdAt.toDb()).update()
    }

    private fun withNotes(list: List<Conclusion>): List<Conclusion> {
        if (list.isEmpty()) return list
        val notes = jdbc.sql("select * from conclusion_note where conclusion_id = any(:ids) order by created_at, id")
            .param("ids", list.map { it.id }.toTypedArray())
            .query { rs, _ -> rs.uuid("conclusion_id") to ConclusionNote(rs.uuid("id"), rs.uuid("author_id"), rs.getString("author_name"), rs.getString("text"), rs.instant("created_at")) }
            .list().groupBy({ it.first }, { it.second })
        return list.map { it.copy(notes = notes[it.id].orEmpty()) }
    }

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = Conclusion(
        id = rs.uuid("id"),
        patientId = rs.uuid("patient_id"),
        visitId = rs.uuid("visit_id"),
        visitItemId = rs.uuid("visit_item_id"),
        authorId = rs.uuid("author_id"),
        authorName = rs.getString("author_name"),
        text = ConclusionText(rs.getString("title"), rs.getString("body")),
        notes = emptyList(),
        createdAt = rs.instant("created_at"),
        updatedAt = rs.instant("updated_at"),
        version = rs.getInt("version"),
    )
}

@Repository
class JdbcConclusionTemplateRepository(private val jdbc: JdbcClient, private val clinic: ClinicContext) : ConclusionTemplateRepository {
    override fun insert(template: ConclusionTemplate) {
        val identity = clinic.current()
        unique {
            jdbc.sql(
                """
                insert into conclusion_template (id, clinic_id, branch_id, owner_id, name, body, created_at, updated_at, version)
                values (:id, :clinicId, :branchId, :ownerId, :name, :body, :createdAt, :updatedAt, 0)
                """,
            ).param("id", template.id).param("clinicId", identity.clinicId).param("branchId", identity.branchId)
                .param("ownerId", template.ownerId).param("name", template.name).param("body", template.body)
                .param("createdAt", template.createdAt.toDb()).param("updatedAt", template.updatedAt.toDb()).update()
        }
    }

    override fun findById(id: UUID): ConclusionTemplate? =
        jdbc.sql("select * from conclusion_template where id = :id and deleted_at is null").param("id", id).query(::map).optional().orElse(null)

    override fun findByOwner(ownerId: UUID): List<ConclusionTemplate> =
        jdbc.sql("select * from conclusion_template where owner_id = :o and deleted_at is null order by lower(name), id").param("o", ownerId).query(::map).list()

    override fun update(template: ConclusionTemplate): ConclusionTemplate {
        val rows = unique {
            jdbc.sql(
                """
                update conclusion_template set name = :name, body = :body, updated_at = :updatedAt, deleted_at = :deletedAt, version = version + 1
                where id = :id and version = :version and deleted_at is null
                """,
            ).param("id", template.id).param("name", template.name).param("body", template.body).param("updatedAt", template.updatedAt.toDb())
                .param("deletedAt", template.deletedAt.toDbOrNull()).param("version", template.version).update()
        }
        requireVersionMatched(rows, "conclusion_template", template.id)
        return template.copy(version = template.version + 1)
    }

    private inline fun <T> unique(block: () -> T): T =
        try {
            block()
        } catch (_: DuplicateKeyException) {
            throw DomainException("conclusion.template_name_taken", DomainException.Kind.CONFLICT)
        }

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = ConclusionTemplate(
        rs.uuid("id"), rs.uuid("owner_id"), rs.getString("name"), rs.getString("body"),
        rs.instant("created_at"), rs.instant("updated_at"), rs.instantOrNull("deleted_at"), rs.getInt("version"),
    )
}
