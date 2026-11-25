package uz.medclinic.clinic.imports.application

/**
 * Column headers and example rows of the downloadable templates, per UI language. Spreadsheets are documents the
 * staff fill in by hand, so (like printed documents) their text is produced on the server.
 * Role names must stay in sync with the web app's `roleTemplate.*` translations.
 */
object ImportTexts {
    val LANGUAGES = listOf("uz-Latn", "uz-Cyrl", "ru")

    private val staffHeaders = mapOf(
        "uz-Latn" to listOf("F.I.Sh.", "Login", "Rol", "Lavozim", "Telefon"),
        "uz-Cyrl" to listOf("Ф.И.Ш.", "Логин", "Рол", "Лавозим", "Телефон"),
        "ru" to listOf("Ф.И.О.", "Логин", "Роль", "Должность", "Телефон"),
    )

    private val staffExample = mapOf(
        "uz-Latn" to listOf("Karimova Dilnoza Aliyevna", "dilnoza", "Qabulxona", "Registrator", "+998 90 123 45 67"),
        "uz-Cyrl" to listOf("Каримова Дилноза Алиевна", "dilnoza", "Қабулхона", "Регистратор", "+998 90 123 45 67"),
        "ru" to listOf("Каримова Дилноза Алиевна", "dilnoza", "Регистратура", "Регистратор", "+998 90 123 45 67"),
    )

    /** Translated names of the role templates accepted in the "role" column (besides the codes themselves). */
    val roleNames: Map<String, String> = buildMap {
        mapOf(
            "ADMIN" to listOf("Administrator", "Администратор"),
            "RECEPTION" to listOf("Qabulxona", "Қабулхона", "Регистратура", "Registratura"),
            "CASHIER" to listOf("Kassir", "Кассир"),
            "DOCTOR" to listOf("Shifokor", "Шифокор", "Врач"),
            "LAB" to listOf("Laborant", "Лаборант", "Laboratoriya", "Лаборатория"),
            "NURSE" to listOf("Hamshira", "Ҳамшира", "Медсестра"),
        ).forEach { (code, names) -> names.forEach { put(it.lowercase(), code) } }
    }

    fun staffHeaders(lang: String): List<String> = staffHeaders[lang] ?: staffHeaders.getValue("uz-Latn")

    fun staffExample(lang: String): List<String> = staffExample[lang] ?: staffExample.getValue("uz-Latn")

    /** Per column, the header in every language (any of them is accepted when reading a file). */
    fun acceptedStaffHeaders(): List<Set<String>> = staffHeaders.getValue("uz-Latn").indices.map { i -> staffHeaders.values.map { it[i] }.toSet() }
}
