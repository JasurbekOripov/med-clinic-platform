package uz.medclinic.clinic.imports.application

import org.springframework.stereotype.Component
import uz.medclinic.clinic.identity.api.NewStaff
import uz.medclinic.clinic.identity.api.StaffDirectory
import uz.medclinic.clinic.imports.domain.ImportColumn
import uz.medclinic.clinic.imports.domain.ImportKind
import uz.medclinic.clinic.imports.domain.ImportRow
import uz.medclinic.clinic.imports.domain.ImportedItem
import uz.medclinic.clinic.imports.domain.Importer
import uz.medclinic.clinic.imports.domain.RowIssue
import uz.medclinic.common.UzPhone

@Component
class StaffImporter(private val directory: StaffDirectory) : Importer {
    override val kind = ImportKind.STAFF

    override val columns = listOf(
        ImportColumn(FULL_NAME, required = true),
        ImportColumn(LOGIN, required = true),
        ImportColumn(ROLE, required = true),
        ImportColumn(POSITION, required = false),
        ImportColumn(PHONE, required = false),
    )

    override fun validate(rows: List<ImportRow>): List<ImportRow> {
        val seenLogins = mutableSetOf<String>()
        return rows.map { row ->
            val errors = mutableListOf<RowIssue>()
            val warnings = mutableListOf<RowIssue>()
            columns.filter { it.required && row.values[it.key].isNullOrBlank() }.forEach { errors += RowIssue(it.key, "validation.not_blank") }

            row.values[FULL_NAME]?.let { name ->
                if (name.length > 200) errors += RowIssue(FULL_NAME, "validation.size", mapOf("max" to 200))
                else if (directory.fullNameExists(name)) warnings += RowIssue(FULL_NAME, "import.name_exists")
            }
            row.values[LOGIN]?.let { raw ->
                val login = directory.normaliseLogin(raw)
                when {
                    login == null -> errors += RowIssue(LOGIN, "validation.login_format")
                    !seenLogins.add(login) -> errors += RowIssue(LOGIN, "import.duplicate_in_file")
                    directory.loginExists(login) -> errors += RowIssue(LOGIN, "staff.login_taken")
                }
            }
            row.values[ROLE]?.let { if (resolveRole(it) == null) errors += RowIssue(ROLE, "validation.unknown_role") }
            row.values[POSITION]?.let { if (it.length > 100) errors += RowIssue(POSITION, "validation.size", mapOf("max" to 100)) }
            row.values[PHONE]?.let { if (UzPhone.parse(it) == null) errors += RowIssue(PHONE, "validation.phone") }
            row.copy(errors = errors, warnings = warnings)
        }
    }

    override fun commit(rows: List<ImportRow>): List<ImportedItem> {
        val created = directory.createAll(
            rows.map { r ->
                NewStaff(
                    login = directory.normaliseLogin(r.values.getValue(LOGIN)!!)!!,
                    fullName = r.values.getValue(FULL_NAME)!!,
                    position = r.values[POSITION],
                    phone = r.values[PHONE],
                    roleId = resolveRole(r.values.getValue(ROLE)!!)!!,
                )
            },
        )
        return created.zip(rows).map { (c, r) ->
            ImportedItem(r.rowNumber, c.fullName, mapOf("login" to c.login, "temporaryPassword" to c.temporaryPassword))
        }
    }

    /** Accepts a template code (DOCTOR), its translated name (Shifokor, Врач) or a custom role's name. */
    private fun resolveRole(text: String) =
        directory.findRoleId(ImportTexts.roleNames[text.trim().lowercase()] ?: text)

    companion object {
        const val FULL_NAME = "fullName"
        const val LOGIN = "login"
        const val ROLE = "role"
        const val POSITION = "position"
        const val PHONE = "phone"
    }
}
