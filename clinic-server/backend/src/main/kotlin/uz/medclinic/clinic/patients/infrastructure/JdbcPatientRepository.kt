package uz.medclinic.clinic.patients.infrastructure

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import uz.medclinic.clinic.patients.domain.DuplicateCandidate
import uz.medclinic.clinic.patients.domain.DuplicateReason
import uz.medclinic.clinic.patients.domain.Gender
import uz.medclinic.clinic.patients.domain.Patient
import uz.medclinic.clinic.patients.domain.PatientDetails
import uz.medclinic.clinic.patients.domain.PatientRepository
import uz.medclinic.clinic.patients.domain.PatientSearch
import uz.medclinic.clinic.shared.ClinicContext
import uz.medclinic.common.NameNormalizer
import uz.medclinic.common.UzPhone
import uz.medclinic.platform.persistence.instant
import uz.medclinic.platform.persistence.instantOrNull
import uz.medclinic.platform.persistence.likePattern
import uz.medclinic.platform.persistence.requireVersionMatched
import uz.medclinic.platform.persistence.toDb
import uz.medclinic.platform.persistence.toDbOrNull
import uz.medclinic.platform.persistence.uuid
import uz.medclinic.platform.persistence.uuidOrNull
import java.sql.ResultSet
import java.time.LocalDate
import java.util.UUID

@Repository
class JdbcPatientRepository(private val jdbc: JdbcClient, private val clinic: ClinicContext) : PatientRepository {
    override fun insert(patient: Patient): Patient {
        val identity = clinic.current()
        val cardNo = jdbc.sql(
            """
            insert into patient (id, clinic_id, branch_id, card_no, last_name, first_name, patronymic, gender, birth_date, phone,
                                 address, notes, search_key, created_by, created_at, updated_at, version)
            values (:id, :clinicId, :branchId, nextval('patient_card_seq'), :lastName, :firstName, :patronymic, :gender,
                    :birthDate, :phone, :address, :notes, :searchKey, :createdBy, :createdAt, :updatedAt, 0)
            returning card_no
            """,
        ).params(params(patient)).param("clinicId", identity.clinicId).param("branchId", identity.branchId)
            .param("createdBy", patient.createdBy).param("createdAt", patient.createdAt.toDb())
            .query(Long::class.java).single()
        return patient.copy(cardNo = cardNo)
    }

    override fun findById(id: UUID): Patient? =
        jdbc.sql("select * from patient where id = :id and deleted_at is null").param("id", id).query(::map).optional().orElse(null)

    override fun search(query: PatientSearch, offset: Long, limit: Int): Pair<List<Patient>, Long> {
        val (where, order, params) = when (query) {
            PatientSearch.Recent -> Triple("true", "updated_at desc, id desc", emptyMap())
            is PatientSearch.ByCard -> Triple("card_no = :card", "card_no", mapOf("card" to query.cardNo))
            is PatientSearch.ByPhone -> Triple("phone like :phone escape '\\'", "last_name, first_name, id", mapOf("phone" to likePattern(query.digits)))
            // Substring match for partial typing, word similarity for typos and spelling variants (Petr/Pyotr).
            is PatientSearch.ByName -> Triple(
                "(search_key like :contains escape '\\' or :key <% search_key)",
                "word_similarity(:key, search_key) desc, last_name, first_name, id",
                mapOf("key" to query.key, "contains" to likePattern(query.key)),
            )
        }
        val total = jdbc.sql("select count(*) from patient where deleted_at is null and $where").params(params)
            .query(Long::class.java).single()
        val items = jdbc.sql("select * from patient where deleted_at is null and $where order by $order limit :limit offset :offset")
            .params(params).param("limit", limit).param("offset", offset).query(::map).list()
        return items to total
    }

    override fun update(patient: Patient): Patient {
        val rows = jdbc.sql(
            """
            update patient
            set last_name = :lastName, first_name = :firstName, patronymic = :patronymic, gender = :gender,
                birth_date = :birthDate, phone = :phone, address = :address, notes = :notes, search_key = :searchKey,
                updated_at = :updatedAt, deleted_at = :deletedAt, version = version + 1
            where id = :id and version = :version and deleted_at is null
            """,
        ).params(params(patient)).param("version", patient.version).param("deletedAt", patient.deletedAt.toDbOrNull()).update()
        requireVersionMatched(rows, "patient", patient.id)
        return patient.copy(version = patient.version + 1)
    }

    /**
     * Possible duplicates: same last + first name (any script) and birth date; or same phone with similar last and
     * first names (family members share phones and surnames, so both parts must be similar).
     */
    override fun duplicates(details: PatientDetails, excludeId: UUID?): List<DuplicateCandidate> {
        val last = NameNormalizer.normalise(details.lastName)
        val first = NameNormalizer.normalise(details.firstName)
        val lastFirst = "$last $first"
        return jdbc.sql(
            """
            select *,
                   case when birth_date = :birthDate and (search_key = :lastFirst or search_key like :lastFirstPrefix escape '\')
                        then 'SAME_NAME_AND_BIRTH_DATE' else 'SAME_PHONE_SIMILAR_NAME' end as reason
            from patient
            where deleted_at is null and (cast(:excludeId as uuid) is null or id <> :excludeId)
              and (
                (birth_date = :birthDate and (search_key = :lastFirst or search_key like :lastFirstPrefix escape '\'))
                or (phone = :phone and similarity(split_part(search_key, ' ', 1), :last) > 0.5
                                   and similarity(split_part(search_key, ' ', 2), :first) > 0.5)
              )
            order by updated_at desc
            limit 5
            """,
        )
            .param("birthDate", details.birthDate).param("lastFirst", lastFirst).param("lastFirstPrefix", likePattern(lastFirst).removePrefix("%").removeSuffix("%") + " %")
            .param("phone", details.phone.e164).param("last", last).param("first", first).param("excludeId", excludeId)
            .query { rs, row -> DuplicateCandidate(map(rs, row), DuplicateReason.valueOf(rs.getString("reason"))) }
            .list()
    }

    private fun params(p: Patient) = mapOf(
        "id" to p.id,
        "lastName" to p.details.lastName,
        "firstName" to p.details.firstName,
        "patronymic" to p.details.patronymic,
        "gender" to p.details.gender.name,
        "birthDate" to p.details.birthDate,
        "phone" to p.details.phone.e164,
        "address" to p.details.address,
        "notes" to p.details.notes,
        "searchKey" to p.details.searchKey,
        "updatedAt" to p.updatedAt.toDb(),
    )

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = Patient(
        id = rs.uuid("id"),
        cardNo = rs.getLong("card_no"),
        details = PatientDetails(
            lastName = rs.getString("last_name"),
            firstName = rs.getString("first_name"),
            patronymic = rs.getString("patronymic"),
            gender = Gender.valueOf(rs.getString("gender")),
            birthDate = rs.getObject("birth_date", LocalDate::class.java),
            phone = UzPhone.of(rs.getString("phone")),
            address = rs.getString("address"),
            notes = rs.getString("notes"),
        ),
        createdBy = rs.uuidOrNull("created_by"),
        createdAt = rs.instant("created_at"),
        updatedAt = rs.instant("updated_at"),
        deletedAt = rs.instantOrNull("deleted_at"),
        version = rs.getInt("version"),
    )
}
