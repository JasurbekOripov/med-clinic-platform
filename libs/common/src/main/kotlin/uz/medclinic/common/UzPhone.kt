package uz.medclinic.common

/**
 * An Uzbekistan phone number normalised to E.164 (`+998` followed by 9 digits).
 *
 * Accepts common user input: spaces, dashes, brackets, a leading `+`, `998…`, or the 9-digit national
 * number. Only the length/country code is validated; operator prefixes change over time and are
 * deliberately not hard-coded.
 */
@JvmInline
value class UzPhone private constructor(val e164: String) {
    override fun toString(): String = e164

    companion object {
        private const val COUNTRY_CODE = "998"
        private const val NATIONAL_LENGTH = 9
        private val ALLOWED_SEPARATORS = Regex("[\\s()\\-.]")

        fun parse(raw: String): UzPhone? {
            val compact = raw.trim().replace(ALLOWED_SEPARATORS, "").removePrefix("+")
            if (compact.isEmpty() || !compact.all(Char::isDigit)) return null
            val national = when {
                compact.length == COUNTRY_CODE.length + NATIONAL_LENGTH && compact.startsWith(COUNTRY_CODE) ->
                    compact.substring(COUNTRY_CODE.length)
                compact.length == NATIONAL_LENGTH -> compact
                else -> return null
            }
            return UzPhone("+$COUNTRY_CODE$national")
        }

        fun of(raw: String): UzPhone =
            requireNotNull(parse(raw)) { "Not a valid Uzbekistan phone number" }
    }
}
