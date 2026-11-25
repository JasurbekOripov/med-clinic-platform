package uz.medclinic.clinic.settings.domain

import uz.medclinic.common.UzPhone
import uz.medclinic.platform.domain.FieldViolation
import uz.medclinic.platform.domain.Text
import uz.medclinic.platform.domain.ValidationException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/** Opening hours of one weekday; `closed` days have no times. */
data class WorkingDay(val day: DayOfWeek, val closed: Boolean, val opensAt: LocalTime?, val closesAt: LocalTime?) {
    companion object {
        fun defaults(): List<WorkingDay> = DayOfWeek.entries.map {
            if (it == DayOfWeek.SUNDAY) WorkingDay(it, true, null, null) else WorkingDay(it, false, LocalTime.of(8, 0), LocalTime.of(18, 0))
        }
    }
}

data class ClinicSettings(
    val name: String,
    val legalName: String?,
    val address: String?,
    val phone: UzPhone?,
    val letterhead: String?,
    val receiptHeader: String?,
    val receiptFooter: String?,
    val workingHours: List<WorkingDay>,
    val idleLockMinutes: Int,
    val hasLogo: Boolean,
    val updatedAt: Instant,
    val version: Int,
) {
    companion object {
        const val DEFAULT_IDLE_LOCK_MINUTES = 10

        /** Validates and normalises editable fields; all problems are reported together. */
        fun validated(
            name: String?,
            legalName: String?,
            address: String?,
            phone: String?,
            letterhead: String?,
            receiptHeader: String?,
            receiptFooter: String?,
            workingHours: List<WorkingDay>?,
            idleLockMinutes: Int?,
        ): Edit {
            val errors = mutableListOf<FieldViolation>()
            fun text(field: String, value: String?, max: Int, multiline: Boolean = false): String? {
                val clean = if (multiline) Text.cleanMultiline(value) else Text.clean(value)
                if (clean != null && clean.length > max) errors += FieldViolation(field, "validation.size", mapOf("max" to max))
                return clean
            }
            val cleanName = text("name", name, 200)
            if (cleanName == null) errors += FieldViolation("name", "validation.not_blank")
            val parsedPhone = Text.clean(phone)?.let { UzPhone.parse(it) ?: run { errors += FieldViolation("phone", "validation.phone"); null } }
            val hours = workingHours ?: WorkingDay.defaults()
            if (hours.map { it.day }.toSet().size != 7 || hours.size != 7) errors += FieldViolation("workingHours", "validation.working_hours")
            hours.forEach { d ->
                if (!d.closed && (d.opensAt == null || d.closesAt == null || !d.closesAt.isAfter(d.opensAt))) {
                    errors += FieldViolation("workingHours.${d.day}", "validation.working_hours")
                }
            }
            val idle = idleLockMinutes ?: DEFAULT_IDLE_LOCK_MINUTES
            if (idle !in 1..240) errors += FieldViolation("idleLockMinutes", "validation.range", mapOf("min" to 1, "max" to 240))
            val edit = Edit(
                cleanName.orEmpty(), text("legalName", legalName, 300), text("address", address, 500), parsedPhone,
                text("letterhead", letterhead, 2000, true), text("receiptHeader", receiptHeader, 1000, true),
                text("receiptFooter", receiptFooter, 1000, true),
                hours.sortedBy { it.day }.map { if (it.closed) it.copy(opensAt = null, closesAt = null) else it }, idle,
            )
            if (errors.isNotEmpty()) throw ValidationException(errors)
            return edit
        }
    }

    data class Edit(
        val name: String,
        val legalName: String?,
        val address: String?,
        val phone: UzPhone?,
        val letterhead: String?,
        val receiptHeader: String?,
        val receiptFooter: String?,
        val workingHours: List<WorkingDay>,
        val idleLockMinutes: Int,
    )
}

/** Logos are printed on documents: PNG/JPEG recognised by content, at most 1 MB. */
object LogoImage {
    const val MAX_BYTES = 1024 * 1024

    fun contentType(bytes: ByteArray): String {
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) throw ValidationException("file", "validation.image_size", mapOf("maxKb" to MAX_BYTES / 1024))
        return when {
            bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte() -> "image/png"
            bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
            else -> throw ValidationException("file", "validation.image_type")
        }
    }
}
