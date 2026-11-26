package uz.medclinic.clinic.lab.infrastructure

import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import uz.medclinic.clinic.lab.domain.FormRow
import uz.medclinic.clinic.lab.domain.LabTemplate
import uz.medclinic.clinic.lab.domain.LabTemplateRepository
import uz.medclinic.clinic.shared.ClinicContext
import uz.medclinic.common.Ids
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
class JdbcLabTemplateRepository(private val jdbc: JdbcClient, private val clinic: ClinicContext, private val json: JsonMapper) : LabTemplateRepository {
    override fun insert(template: LabTemplate) {
        val identity = clinic.current()
        unique {
            jdbc.sql(
                """
                insert into lab_template (id, clinic_id, branch_id, name, current_version, active, created_at, updated_at, version)
                values (:id, :clinicId, :branchId, :name, 1, :active, :createdAt, :updatedAt, 0)
                """,
            ).param("id", template.id).param("clinicId", identity.clinicId).param("branchId", identity.branchId)
                .param("name", template.name).param("active", template.active)
                .param("createdAt", template.createdAt.toDb()).param("updatedAt", template.updatedAt.toDb()).update()
        }
        insertVersion(template.versionId, template.id, 1, template.rows, null, template.createdAt)
        writeServices(template)
    }

    override fun findById(id: UUID): LabTemplate? = load("t.id = :id and t.deleted_at is null", mapOf("id" to id)).firstOrNull()

    override fun findAll(): List<LabTemplate> = load("t.deleted_at is null", emptyMap())

    override fun update(template: LabTemplate, newRows: Boolean, by: UUID?): LabTemplate {
        val versionNo = if (newRows) template.versionNo + 1 else template.versionNo
        val rows = unique {
            jdbc.sql(
                """
                update lab_template set name = :name, current_version = :versionNo, active = :active, updated_at = :updatedAt,
                       deleted_at = :deletedAt, version = version + 1
                where id = :id and version = :version and deleted_at is null
                """,
            ).param("id", template.id).param("name", template.name).param("versionNo", versionNo).param("active", template.active)
                .param("updatedAt", template.updatedAt.toDb()).param("deletedAt", template.deletedAt.toDbOrNull())
                .param("version", template.version).update()
        }
        requireVersionMatched(rows, "lab_template", template.id)
        val versionId = if (newRows) Ids.newId().also { insertVersion(it, template.id, versionNo, template.rows, by, template.updatedAt) } else template.versionId
        writeServices(template)
        return template.copy(versionNo = versionNo, versionId = versionId, version = template.version + 1)
    }

    override fun rowsOfVersion(versionId: UUID): List<FormRow>? =
        jdbc.sql("select rows from lab_template_version where id = :id").param("id", versionId)
            .query { rs, _ -> parse(rs.getString("rows")) }.optional().orElse(null)

    private fun insertVersion(id: UUID, templateId: UUID, versionNo: Int, rows: List<FormRow>, by: UUID?, at: java.time.Instant) {
        jdbc.sql(
            "insert into lab_template_version (id, template_id, version_no, rows, created_by, created_at) values (:id, :t, :no, cast(:rows as jsonb), :by, :at)",
        ).param("id", id).param("t", templateId).param("no", versionNo).param("rows", json.writeValueAsString(rows))
            .param("by", by).param("at", at.toDb()).update()
    }

    /** A deleted form releases its services so another form can be linked to them. */
    private fun writeServices(t: LabTemplate) {
        jdbc.sql("delete from lab_template_service where template_id = :id").param("id", t.id).update()
        if (t.deletedAt != null) return
        unique {
            t.serviceIds.forEach {
                jdbc.sql("insert into lab_template_service (template_id, service_id) values (:t, :s)").param("t", t.id).param("s", it).update()
            }
        }
    }

    private fun load(where: String, params: Map<String, Any>): List<LabTemplate> {
        val list = jdbc.sql(
            """
            select t.*, v.id as version_id, v.rows from lab_template t
            join lab_template_version v on v.template_id = t.id and v.version_no = t.current_version
            where $where order by lower(t.name), t.id
            """,
        ).params(params).query(::map).list()
        if (list.isEmpty()) return list
        val services = jdbc.sql("select * from lab_template_service where template_id = any(:ids)").param("ids", list.map { it.id }.toTypedArray())
            .query { rs, _ -> rs.uuid("template_id") to rs.uuid("service_id") }.list().groupBy({ it.first }, { it.second })
        return list.map { it.copy(serviceIds = services[it.id].orEmpty().toSet()) }
    }

    private fun parse(rows: String): List<FormRow> = json.readValue(rows, object : TypeReference<List<FormRow>>() {})

    private inline fun <T> unique(block: () -> T): T =
        try {
            block()
        } catch (_: DuplicateKeyException) {
            throw DomainException("lab.template_conflict", DomainException.Kind.CONFLICT)
        }

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = LabTemplate(
        id = rs.uuid("id"),
        name = rs.getString("name"),
        serviceIds = emptySet(),
        rows = parse(rs.getString("rows")),
        versionNo = rs.getInt("current_version"),
        versionId = rs.uuid("version_id"),
        active = rs.getBoolean("active"),
        createdAt = rs.instant("created_at"),
        updatedAt = rs.instant("updated_at"),
        deletedAt = rs.instantOrNull("deleted_at"),
        version = rs.getInt("version"),
    )
}
