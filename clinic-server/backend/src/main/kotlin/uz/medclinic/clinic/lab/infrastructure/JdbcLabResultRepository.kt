package uz.medclinic.clinic.lab.infrastructure

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import uz.medclinic.clinic.lab.domain.LabResult
import uz.medclinic.clinic.lab.domain.LabResultRepository
import uz.medclinic.clinic.lab.domain.PhysioState
import uz.medclinic.clinic.lab.domain.ResultStatus
import uz.medclinic.clinic.lab.domain.RowValue
import uz.medclinic.clinic.shared.ClinicContext
import uz.medclinic.platform.persistence.instant
import uz.medclinic.platform.persistence.instantOrNull
import uz.medclinic.platform.persistence.requireVersionMatched
import uz.medclinic.platform.persistence.toDb
import uz.medclinic.platform.persistence.toDbOrNull
import uz.medclinic.platform.persistence.uuid
import uz.medclinic.platform.persistence.uuidOrNull
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@Repository
class JdbcLabResultRepository(private val jdbc: JdbcClient, private val clinic: ClinicContext, private val json: JsonMapper) : LabResultRepository {
    override fun insertIfAbsent(result: LabResult): Boolean {
        val identity = clinic.current()
        return jdbc.sql(
            """
            insert into lab_result (id, clinic_id, branch_id, visit_item_id, visit_id, patient_id, service_name, template_id, status,
                                    created_at, updated_at, version)
            values (:id, :clinicId, :branchId, :itemId, :visitId, :patientId, :serviceName, :templateId, 'WAITING', :createdAt, :createdAt, 0)
            on conflict (visit_item_id) do nothing
            """,
        ).param("id", result.id).param("clinicId", identity.clinicId).param("branchId", identity.branchId)
            .param("itemId", result.visitItemId).param("visitId", result.visitId).param("patientId", result.patientId)
            .param("serviceName", result.serviceName).param("templateId", result.templateId).param("createdAt", result.createdAt.toDb())
            .update() == 1
    }

    override fun findById(id: UUID): LabResult? =
        jdbc.sql("select * from lab_result where id = :id").param("id", id).query(::map).optional().orElse(null)

    override fun update(result: LabResult): LabResult {
        val rows = jdbc.sql(
            """
            update lab_result set template_version_id = :versionId, state = :state, custom_state = :customState, values = cast(:values as jsonb),
                   comment = :comment, status = :status, entered_by = :enteredBy, ready_by = :readyBy, ready_by_name = :readyByName,
                   ready_at = :readyAt, corrected_at = :correctedAt, correction_reason = :correctionReason, updated_at = :updatedAt,
                   version = version + 1
            where id = :id and version = :version
            """,
        )
            .param("id", result.id).param("versionId", result.templateVersionId).param("state", result.state.name)
            .param("customState", result.customState).param("values", json.writeValueAsString(result.values.mapKeys { it.key.toString() }))
            .param("comment", result.comment).param("status", result.status.name).param("enteredBy", result.enteredBy)
            .param("readyBy", result.readyBy).param("readyByName", result.readyByName).param("readyAt", result.readyAt.toDbOrNull())
            .param("correctedAt", result.correctedAt.toDbOrNull()).param("correctionReason", result.correctionReason)
            .param("updatedAt", result.updatedAt.toDb()).param("version", result.version).update()
        requireVersionMatched(rows, "lab_result", result.id)
        return result.copy(version = result.version + 1)
    }

    override fun workList(dayStart: Instant, dayEnd: Instant): List<LabResult> =
        jdbc.sql(
            """
            select * from lab_result
            where status <> 'READY' or (created_at >= :start and created_at < :end)
            order by case status when 'READY' then 1 else 0 end, created_at, id
            limit 500
            """,
        ).param("start", dayStart.toDb()).param("end", dayEnd.toDb()).query(::map).list()

    override fun findByPatient(patientId: UUID, readyOnly: Boolean): List<LabResult> =
        jdbc.sql(
            "select * from lab_result where patient_id = :p ${if (readyOnly) "and status = 'READY'" else ""} order by created_at desc, id desc",
        ).param("p", patientId).query(::map).list()

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int): LabResult {
        val raw: Map<String, RowValue> = json.readValue(rs.getString("values"), object : TypeReference<Map<String, RowValue>>() {})
        return LabResult(
            id = rs.uuid("id"),
            visitItemId = rs.uuid("visit_item_id"),
            visitId = rs.uuid("visit_id"),
            patientId = rs.uuid("patient_id"),
            serviceName = rs.getString("service_name"),
            templateId = rs.uuid("template_id"),
            templateVersionId = rs.uuidOrNull("template_version_id"),
            state = PhysioState.valueOf(rs.getString("state")),
            customState = rs.getString("custom_state"),
            values = raw.mapKeys { UUID.fromString(it.key) },
            comment = rs.getString("comment"),
            status = ResultStatus.valueOf(rs.getString("status")),
            enteredBy = rs.uuidOrNull("entered_by"),
            readyBy = rs.uuidOrNull("ready_by"),
            readyByName = rs.getString("ready_by_name"),
            readyAt = rs.instantOrNull("ready_at"),
            correctedAt = rs.instantOrNull("corrected_at"),
            correctionReason = rs.getString("correction_reason"),
            createdAt = rs.instant("created_at"),
            updatedAt = rs.instant("updated_at"),
            version = rs.getInt("version"),
        )
    }
}
