package uz.medclinic.clinic.identity.domain

import uz.medclinic.clinic.shared.Permission
import uz.medclinic.clinic.shared.Permission.*
import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.domain.Text
import uz.medclinic.platform.domain.ValidationException
import java.time.Instant
import java.util.UUID

/** Role templates created for every clinic. ADMIN and VENDOR_SUPPORT are system roles: always everything. */
enum class RoleTemplate(val system: Boolean, val defaults: Set<Permission>) {
    ADMIN(true, Permission.entries.toSet()),
    RECEPTION(false, setOf(PATIENT_VIEW, PATIENT_EDIT, VISIT_CREATE, VISIT_EDIT_UNPAID, QUEUE_MANAGE, RESULTS_HANDOVER)),
    CASHIER(false, setOf(PATIENT_VIEW, PAYMENT_ACCEPT, SHIFT_MANAGE, DEBT_VIEW)),
    DOCTOR(false, setOf(PATIENT_VIEW, MEDICAL_HISTORY_VIEW, CONSULTATION_WRITE, SERVICE_ORDER, QUEUE_MANAGE)),
    LAB(false, setOf(PATIENT_VIEW, LAB_WORK)),
    NURSE(false, setOf(PATIENT_VIEW, MEDICAL_HISTORY_VIEW, INPATIENT_WORK)),
    VENDOR_SUPPORT(true, Permission.entries.toSet()),
    ;

    /** Templates the Admin can give to staff; the vendor's account is created by the installer only. */
    val assignable: Boolean get() = this != VENDOR_SUPPORT
}

data class Role(
    val id: UUID,
    val template: RoleTemplate?,
    val name: String?,
    val system: Boolean,
    val permissions: Set<Permission>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val version: Int,
) {
    init {
        require(template != null || !name.isNullOrBlank()) { "a custom role needs a name" }
    }

    /** System roles always have every permission, even ones added to the catalogue later. */
    val effectivePermissions: Set<Permission> get() = if (system) Permission.entries.toSet() else permissions

    val assignable: Boolean get() = template?.assignable ?: true

    fun edited(name: String?, permissions: Set<Permission>, now: Instant): Role {
        if (system) throw DomainException("role.system_locked")
        return copy(name = if (template == null) RoleName.validate(name) else Text.clean(name), permissions = permissions, updatedAt = now)
    }

    companion object {
        fun custom(id: UUID, name: String?, permissions: Set<Permission>, now: Instant) =
            Role(id, null, RoleName.validate(name), false, permissions, now, now, null, 0)

        fun fromTemplate(id: UUID, template: RoleTemplate, now: Instant) =
            Role(id, template, null, template.system, template.defaults, now, now, null, 0)
    }
}

object RoleName {
    fun validate(raw: String?): String {
        val name = Text.clean(raw) ?: throw ValidationException("name", "validation.not_blank")
        if (name.length > 100) throw ValidationException("name", "validation.size", mapOf("max" to 100))
        return name
    }
}

fun parsePermissions(codes: Collection<String>, field: String): Set<Permission> {
    val unknown = codes.filter { code -> Permission.entries.none { it.name == code } }
    if (unknown.isNotEmpty()) throw ValidationException(field, "validation.unknown_permissions", mapOf("permissions" to unknown))
    return codes.map(Permission::valueOf).toSet()
}
