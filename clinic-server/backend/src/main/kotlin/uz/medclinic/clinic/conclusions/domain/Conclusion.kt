package uz.medclinic.clinic.conclusions.domain

import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.domain.FieldViolation
import uz.medclinic.platform.domain.Text
import uz.medclinic.platform.domain.ValidationException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

const val MAX_BODY = 20_000

data class ConclusionText(val title: String, val body: String) {
    companion object {
        fun of(title: String?, body: String?): ConclusionText {
            val errors = mutableListOf<FieldViolation>()
            val cleanTitle = Text.clean(title)
            if (cleanTitle == null) errors += FieldViolation("title", "validation.not_blank")
            else if (cleanTitle.length > 200) errors += FieldViolation("title", "validation.size", mapOf("max" to 200))
            val cleanBody = Text.cleanMultiline(body)
            if (cleanBody == null) errors += FieldViolation("body", "validation.not_blank")
            else if (cleanBody.length > MAX_BODY) errors += FieldViolation("body", "validation.size", mapOf("max" to MAX_BODY))
            if (errors.isNotEmpty()) throw ValidationException(errors)
            return ConclusionText(cleanTitle!!, cleanBody!!)
        }
    }
}

data class ConclusionNote(val id: UUID, val authorId: UUID, val authorName: String, val text: String, val createdAt: Instant)

data class Conclusion(
    val id: UUID,
    val patientId: UUID,
    val visitId: UUID,
    val visitItemId: UUID,
    val authorId: UUID,
    val authorName: String,
    val text: ConclusionText,
    val notes: List<ConclusionNote>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Int,
) {
    /** A medical record is fixed once its day is over; until then the author may correct it. */
    fun editableBy(staffId: UUID, today: LocalDate, writtenOn: LocalDate): Boolean = staffId == authorId && writtenOn == today

    fun edited(by: UUID, text: ConclusionText, today: LocalDate, writtenOn: LocalDate, now: Instant): Conclusion {
        if (by != authorId) throw DomainException("conclusion.not_author", DomainException.Kind.FORBIDDEN)
        if (writtenOn != today) throw DomainException("conclusion.locked")
        return copy(text = text, updatedAt = now)
    }

    companion object {
        fun noteText(raw: String?): String {
            val clean = Text.cleanMultiline(raw) ?: throw ValidationException("text", "validation.not_blank")
            if (clean.length > 5_000) throw ValidationException("text", "validation.size", mapOf("max" to 5_000))
            return clean
        }
    }
}

data class ConclusionTemplate(
    val id: UUID,
    val ownerId: UUID,
    val name: String,
    val body: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val version: Int,
) {
    companion object {
        fun validated(name: String?, body: String?): Pair<String, String> {
            val errors = mutableListOf<FieldViolation>()
            val cleanName = Text.clean(name)
            if (cleanName == null) errors += FieldViolation("name", "validation.not_blank")
            else if (cleanName.length > 100) errors += FieldViolation("name", "validation.size", mapOf("max" to 100))
            val cleanBody = Text.cleanMultiline(body)
            if (cleanBody == null) errors += FieldViolation("body", "validation.not_blank")
            else if (cleanBody.length > MAX_BODY) errors += FieldViolation("body", "validation.size", mapOf("max" to MAX_BODY))
            if (errors.isNotEmpty()) throw ValidationException(errors)
            return cleanName!! to cleanBody!!
        }
    }
}

interface ConclusionRepository {
    fun insert(conclusion: Conclusion)
    fun findById(id: UUID): Conclusion?
    fun findByPatient(patientId: UUID): List<Conclusion>
    fun update(conclusion: Conclusion): Conclusion
    fun addNote(conclusionId: UUID, note: ConclusionNote)
}

interface ConclusionTemplateRepository {
    fun insert(template: ConclusionTemplate)
    fun findById(id: UUID): ConclusionTemplate?
    fun findByOwner(ownerId: UUID): List<ConclusionTemplate>
    fun update(template: ConclusionTemplate): ConclusionTemplate
}
