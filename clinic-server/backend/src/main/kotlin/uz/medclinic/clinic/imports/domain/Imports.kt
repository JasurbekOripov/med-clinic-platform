package uz.medclinic.clinic.imports.domain

/** What can be imported. Price list and patients are added at their milestones. */
enum class ImportKind { STAFF }

/** One problem in one cell/row. Codes are translated by the UI like any other error code. */
data class RowIssue(val column: String?, val code: String, val params: Map<String, Any?> = emptyMap())

/** A parsed spreadsheet row: raw cell texts by column key, plus errors (block import) and warnings (inform only). */
data class ImportRow(
    val rowNumber: Int,
    val values: Map<String, String?>,
    val errors: List<RowIssue> = emptyList(),
    val warnings: List<RowIssue> = emptyList(),
) {
    val valid: Boolean get() = errors.isEmpty()
}

/** Result of importing one row, shown once to the user (e.g. a staff member's temporary password). */
data class ImportedItem(val rowNumber: Int, val label: String, val details: Map<String, String>)

/**
 * A kind-specific importer. [validate] must be pure apart from read-only lookups, so it can run at preview and
 * again at commit (data may have changed in between).
 */
interface Importer {
    val kind: ImportKind
    val columns: List<ImportColumn>
    fun validate(rows: List<ImportRow>): List<ImportRow>
    fun commit(rows: List<ImportRow>): List<ImportedItem>
}

data class ImportColumn(val key: String, val required: Boolean)
