package uz.medclinic.common

import java.text.Normalizer

/**
 * Turns names typed in Uzbek Latin, Uzbek Cyrillic or Russian into one comparable, lower-case Latin form, so that
 * "Тошматов", "Toshmatov" and "Toʻshmatov" find each other. Used only for searching and duplicate detection, never
 * for display.
 *
 * Rules (deliberately simple and predictable):
 * - Cyrillic letters are transliterated to Uzbek Latin spelling (х→x, ҳ→h, ш→sh, ч→ch, ў→o, ғ→g, қ→q, ц→ts …);
 * - all apostrophe variants (' ʻ ʼ ` ’ ‘) are removed, so oʻ/o' → o and gʻ → g;
 * - Russian-style Latin spellings are folded to the Uzbek ones (kh→x, yo/ye/yu/ya keep their letters);
 * - accents are stripped, everything except letters, digits and single spaces is removed.
 */
object NameNormalizer {
    private val CYRILLIC = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'ғ' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo", 'ж' to "j",
        'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'қ' to "q", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o",
        'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ў' to "o", 'ф' to "f", 'х' to "x", 'ҳ' to "h",
        'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "sh", 'ъ' to "", 'ы' to "i", 'ь' to "", 'э' to "e", 'ю' to "yu",
        'я' to "ya",
    )
    private val APOSTROPHES = Regex("['ʻʼ`’‘ʹ]")
    private val NOT_WORD = Regex("[^a-z0-9 ]")
    private val SPACES = Regex("\\s+")
    private val MARKS = Regex("\\p{M}+")

    fun normalise(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val lower = raw.trim().lowercase()
        val transliterated = buildString(lower.length + 8) {
            lower.forEach { c -> append(CYRILLIC[c] ?: c.toString()) }
        }
        val folded = Normalizer.normalize(transliterated.replace(APOSTROPHES, ""), Normalizer.Form.NFD)
            .replace(MARKS, "")
            .replace("kh", "x")
        return folded.replace(NOT_WORD, " ").replace(SPACES, " ").trim()
    }

    /** "Last First Patronymic" normalised, the form stored for searching. */
    fun fullName(lastName: String?, firstName: String?, patronymic: String?): String =
        listOf(lastName, firstName, patronymic).map(::normalise).filter { it.isNotEmpty() }.joinToString(" ")
}
