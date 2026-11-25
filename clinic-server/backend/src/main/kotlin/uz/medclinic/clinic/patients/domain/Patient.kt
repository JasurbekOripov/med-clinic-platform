package uz.medclinic.clinic.patients.domain

import uz.medclinic.common.NameNormalizer
import uz.medclinic.common.UzPhone
import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.domain.FieldViolation
import uz.medclinic.platform.domain.Text
import uz.medclinic.platform.domain.ValidationException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class Gender { MALE, FEMALE }

/** Editable patient data, validated and normalised. No ID documents (decided): identity is name + birth date + phone. */
data class PatientDetails(
    val lastName: String,
    val firstName: String,
    val patronymic: String?,
    val gender: Gender,
    val birthDate: LocalDate,
    val phone: UzPhone,
    val address: String?,
    val notes: String?,
) {
    val fullName: String get() = listOfNotNull(lastName, firstName, patronymic).joinToString(" ")

    val searchKey: String get() = NameNormalizer.fullName(lastName, firstName, patronymic)

    companion object {
        private val EARLIEST = LocalDate.of(1900, 1, 1)

        fun of(
            lastName: String?,
            firstName: String?,
            patronymic: String?,
            gender: Gender?,
            birthDate: LocalDate?,
            phone: String?,
            address: String?,
            notes: String?,
            today: LocalDate,
        ): PatientDetails {
            val errors = mutableListOf<FieldViolation>()
            fun name(field: String, value: String?, required: Boolean): String? {
                val clean = Text.clean(value)
                if (clean == null && required) errors += FieldViolation(field, "validation.not_blank")
                if (clean != null && clean.length > 100) errors += FieldViolation(field, "validation.size", mapOf("max" to 100))
                return clean
            }
            val last = name("lastName", lastName, true)
            val first = name("firstName", firstName, true)
            val middle = name("patronymic", patronymic, false)
            if (gender == null) errors += FieldViolation("gender", "validation.not_null")
            when {
                birthDate == null -> errors += FieldViolation("birthDate", "validation.not_null")
                birthDate.isAfter(today) -> errors += FieldViolation("birthDate", "validation.birth_date_future")
                birthDate.isBefore(EARLIEST) -> errors += FieldViolation("birthDate", "validation.birth_date_too_old")
            }
            val parsedPhone = Text.clean(phone)?.let { UzPhone.parse(it) ?: run { errors += FieldViolation("phone", "validation.phone"); null } }
            if (Text.clean(phone) == null) errors += FieldViolation("phone", "validation.not_blank")
            val cleanAddress = Text.clean(address)
            if (cleanAddress != null && cleanAddress.length > 500) errors += FieldViolation("address", "validation.size", mapOf("max" to 500))
            val cleanNotes = Text.cleanMultiline(notes)
            if (cleanNotes != null && cleanNotes.length > 2000) errors += FieldViolation("notes", "validation.size", mapOf("max" to 2000))
            if (errors.isNotEmpty()) throw ValidationException(errors)
            return PatientDetails(last!!, first!!, middle, gender!!, birthDate!!, parsedPhone!!, cleanAddress, cleanNotes)
        }
    }
}

data class Patient(
    val id: UUID,
    val cardNo: Long,
    val details: PatientDetails,
    val createdBy: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val version: Int,
) {
    fun withDetails(details: PatientDetails, now: Instant) = copy(details = details, updatedAt = now)

    fun deleted(now: Instant): Patient {
        if (deletedAt != null) throw DomainException("patient.already_deleted")
        return copy(deletedAt = now, updatedAt = now)
    }
}

data class DuplicateCandidate(val patient: Patient, val reason: DuplicateReason)

/** Why an existing patient might be the same person. */
enum class DuplicateReason { SAME_NAME_AND_BIRTH_DATE, SAME_PHONE_SIMILAR_NAME }

interface PatientRepository {
    /** Assigns the next card number. */
    fun insert(patient: Patient): Patient
    fun findById(id: UUID): Patient?
    fun search(query: PatientSearch, offset: Long, limit: Int): Pair<List<Patient>, Long>
    fun update(patient: Patient): Patient
    fun duplicates(details: PatientDetails, excludeId: UUID?): List<DuplicateCandidate>
}

/** A parsed search box input. */
sealed interface PatientSearch {
    data object Recent : PatientSearch
    data class ByCard(val cardNo: Long) : PatientSearch
    data class ByPhone(val digits: String) : PatientSearch
    data class ByName(val key: String) : PatientSearch

    companion object {
        /**
         * One search box for everything: up to 6 digits = card number, 7+ digits = phone (any part of it),
         * anything else = name in any script.
         */
        fun parse(raw: String?): PatientSearch {
            val text = Text.clean(raw) ?: return Recent
            val digitsOnly = text.replace(Regex("[\\s+()\\-]"), "")
            if (digitsOnly.isNotEmpty() && digitsOnly.all(Char::isDigit)) {
                return if (digitsOnly.length <= 6) ByCard(digitsOnly.toLong()) else ByPhone(digitsOnly)
            }
            val key = NameNormalizer.normalise(text)
            return if (key.isEmpty()) Recent else ByName(key)
        }
    }
}
