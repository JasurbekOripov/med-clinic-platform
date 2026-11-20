package uz.medclinic.vendor.tenancy.domain

import uz.medclinic.common.UzPhone
import uz.medclinic.vendor.shared.domain.DomainException
import uz.medclinic.vendor.shared.domain.FieldViolation
import uz.medclinic.vendor.shared.domain.Text
import uz.medclinic.vendor.shared.domain.ValidationException
import uz.medclinic.vendor.tenancy.api.TenantStatus
import java.time.Instant
import java.util.UUID

/** Editable clinic fields, validated and normalised. */
data class ClinicDetails(
    val name: String,
    val legalName: String?,
    val ownerName: String,
    val phone: UzPhone,
    val address: String?,
    val region: Region,
    val notes: String?,
) {
    companion object {
        fun of(
            name: String?,
            legalName: String?,
            ownerName: String?,
            phone: String?,
            address: String?,
            region: Region?,
            notes: String?,
        ): ClinicDetails {
            val v = Violations()
            val cleanName = v.required("name", Text.clean(name), 200)
            val cleanOwner = v.required("ownerName", Text.clean(ownerName), 200)
            val cleanLegal = v.optional("legalName", Text.clean(legalName), 300)
            val cleanAddress = v.optional("address", Text.clean(address), 500)
            val cleanNotes = v.optional("notes", Text.cleanMultiline(notes), 4000)
            val parsedPhone = v.phone("phone", phone, required = true)
            if (region == null) v.add("region", "validation.not_null")
            v.throwIfAny()
            return ClinicDetails(cleanName!!, cleanLegal, cleanOwner!!, parsedPhone!!, cleanAddress, region!!, cleanNotes)
        }
    }
}

data class Clinic(
    val id: UUID,
    val code: String,
    val details: ClinicDetails,
    val status: TenantStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val version: Int,
) {
    val archived: Boolean get() = status == TenantStatus.ARCHIVED

    fun withDetails(details: ClinicDetails, now: Instant): Clinic {
        if (archived) throw DomainException("clinic.archived")
        return copy(details = details, updatedAt = now)
    }

    fun withStatus(target: TenantStatus, now: Instant): Clinic {
        if (!status.canTransitionTo(target)) throw invalidTransition(status, target)
        return copy(status = target, updatedAt = now)
    }

    fun deleted(now: Instant): Clinic {
        if (!archived) throw DomainException("clinic.delete_requires_archived")
        return copy(deletedAt = now, updatedAt = now)
    }
}

data class BranchDetails(val name: String, val address: String?, val phone: UzPhone?) {
    companion object {
        fun of(name: String?, address: String?, phone: String?): BranchDetails {
            val v = Violations()
            val cleanName = v.required("name", Text.clean(name), 200)
            val cleanAddress = v.optional("address", Text.clean(address), 500)
            val parsedPhone = v.phone("phone", phone, required = false)
            v.throwIfAny()
            return BranchDetails(cleanName!!, cleanAddress, parsedPhone)
        }
    }
}

data class Branch(
    val id: UUID,
    val clinicId: UUID,
    val code: String,
    val details: BranchDetails,
    val status: TenantStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val version: Int,
) {
    val archived: Boolean get() = status == TenantStatus.ARCHIVED

    fun withDetails(details: BranchDetails, now: Instant): Branch {
        if (archived) throw DomainException("branch.archived")
        return copy(details = details, updatedAt = now)
    }

    fun withStatus(target: TenantStatus, now: Instant): Branch {
        if (!status.canTransitionTo(target)) throw invalidTransition(status, target)
        return copy(status = target, updatedAt = now)
    }

    fun deleted(now: Instant): Branch {
        if (!archived) throw DomainException("branch.delete_requires_archived")
        return copy(deletedAt = now, updatedAt = now)
    }

    companion object {
        /** Branch codes extend the clinic code: `CL-0001` → `CL-0001-01`. */
        fun code(clinicCode: String, number: Int): String = "$clinicCode-" + number.toString().padStart(2, '0')
    }
}

private fun invalidTransition(from: TenantStatus, to: TenantStatus) =
    DomainException("tenant.invalid_status_transition", params = mapOf("from" to from, "to" to to))

/** Collects all field problems so the UI can show them at once. */
private class Violations {
    private val items = mutableListOf<FieldViolation>()

    fun add(field: String, code: String, params: Map<String, Any?> = emptyMap()) {
        items += FieldViolation(field, code, params)
    }

    fun required(field: String, value: String?, max: Int): String? {
        if (value == null) add(field, "validation.not_blank") else if (value.length > max) add(field, "validation.size", mapOf("max" to max))
        return value
    }

    fun optional(field: String, value: String?, max: Int): String? {
        if (value != null && value.length > max) add(field, "validation.size", mapOf("max" to max))
        return value
    }

    fun phone(field: String, raw: String?, required: Boolean): UzPhone? {
        val text = Text.clean(raw)
        if (text == null) {
            if (required) add(field, "validation.not_blank")
            return null
        }
        return UzPhone.parse(text) ?: run { add(field, "validation.phone"); null }
    }

    fun throwIfAny() {
        if (items.isNotEmpty()) throw ValidationException(items.toList())
    }
}
