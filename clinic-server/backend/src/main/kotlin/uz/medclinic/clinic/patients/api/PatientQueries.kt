package uz.medclinic.clinic.patients.api

import java.time.LocalDate
import java.util.UUID

/** What other modules (visits, queue, lab, …) need to know about a patient. */
data class PatientSummary(
    val id: UUID,
    val cardNo: Long,
    val fullName: String,
    val gender: String,
    val birthDate: LocalDate,
    val phone: String,
)

interface PatientQueries {
    fun find(id: UUID): PatientSummary?
}
