package uz.medclinic.vendor.shared.domain

/** Input normalisation used by every module before persisting free text. */
object Text {
    private val WHITESPACE = Regex("\\s+")

    /** Trims and collapses inner whitespace; blank becomes null. */
    fun clean(value: String?): String? = value?.trim()?.replace(WHITESPACE, " ")?.takeIf { it.isNotEmpty() }

    /** Trims only (keeps line breaks), for multi-line notes; blank becomes null. */
    fun cleanMultiline(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
}
