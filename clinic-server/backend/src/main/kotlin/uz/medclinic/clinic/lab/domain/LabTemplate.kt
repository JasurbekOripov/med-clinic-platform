package uz.medclinic.clinic.lab.domain

import uz.medclinic.platform.domain.FieldViolation
import uz.medclinic.platform.domain.Text
import uz.medclinic.platform.domain.ValidationException
import java.time.Instant
import java.util.UUID

data class LabTemplate(
    val id: UUID,
    val name: String,
    val serviceIds: Set<UUID>,
    val rows: List<FormRow>,
    /** Number of the current rows version (1, 2, ...); results reference a specific version. */
    val versionNo: Int,
    /** Id of the current version row. */
    val versionId: UUID,
    val active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val version: Int,
) {
    companion object {
        /** Cleans names and validates the whole form; throws with every problem at once. */
        fun validated(name: String?, rows: List<FormRow>): Pair<String, List<FormRow>> {
            val errors = mutableListOf<FieldViolation>()
            val cleanName = Text.clean(name)
            if (cleanName == null) errors += FieldViolation("name", "validation.not_blank")
            else if (cleanName.length > 200) errors += FieldViolation("name", "validation.size", mapOf("max" to 200))
            val cleanRows = rows.map { r ->
                r.copy(
                    name = Text.clean(r.name) ?: "",
                    unit = Text.clean(r.unit),
                    options = r.options.map { it.copy(label = Text.clean(it.label) ?: "") },
                    ranges = r.ranges.map { it.copy(customState = Text.clean(it.customState), text = Text.clean(it.text)) },
                )
            }
            errors += FormValidator.validate(cleanRows)
            if (errors.isNotEmpty()) throw ValidationException(errors)
            return cleanName!! to cleanRows
        }
    }
}

interface LabTemplateRepository {
    fun insert(template: LabTemplate)
    fun findById(id: UUID): LabTemplate?
    fun findAll(): List<LabTemplate>

    /** Saves name, services and active flag; when [newRows] is true also stores a new rows version. */
    fun update(template: LabTemplate, newRows: Boolean, by: UUID?): LabTemplate

    /** Rows of a specific (possibly older) version, for results entered with it. */
    fun rowsOfVersion(versionId: UUID): List<FormRow>?
}
