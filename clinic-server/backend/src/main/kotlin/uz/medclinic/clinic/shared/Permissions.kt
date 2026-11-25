package uz.medclinic.clinic.shared

/**
 * Every screen and action of the clinic app is guarded by one of these permissions (never by a role name).
 * Roles are data (see the identity module); this catalogue only grows: never rename or reuse a code.
 * Permissions of modules that are not built yet already exist so role templates are complete from day one.
 */
enum class Permission(val group: PermissionGroup) {
    // Administration
    STAFF_VIEW(PermissionGroup.ADMINISTRATION),
    STAFF_MANAGE(PermissionGroup.ADMINISTRATION),
    ROLE_MANAGE(PermissionGroup.ADMINISTRATION),
    SETTINGS_MANAGE(PermissionGroup.ADMINISTRATION),
    AUDIT_VIEW(PermissionGroup.ADMINISTRATION),
    LICENSE_MANAGE(PermissionGroup.ADMINISTRATION),
    DATA_IMPORT(PermissionGroup.ADMINISTRATION),

    // Registration
    PATIENT_VIEW(PermissionGroup.REGISTRATION),
    PATIENT_EDIT(PermissionGroup.REGISTRATION),
    PATIENT_DELETE(PermissionGroup.REGISTRATION),
    VISIT_CREATE(PermissionGroup.REGISTRATION),
    VISIT_EDIT_UNPAID(PermissionGroup.REGISTRATION),
    QUEUE_MANAGE(PermissionGroup.REGISTRATION),
    RESULTS_HANDOVER(PermissionGroup.REGISTRATION),

    // Cashier
    PAYMENT_ACCEPT(PermissionGroup.CASHIER),
    SHIFT_MANAGE(PermissionGroup.CASHIER),
    DEBT_VIEW(PermissionGroup.CASHIER),

    // Money and exceptions (Admin by default)
    REFUND(PermissionGroup.FINANCE),
    PAID_SERVICE_CANCEL(PermissionGroup.FINANCE),
    EMERGENCY_ALLOW(PermissionGroup.FINANCE),
    PRICE_LIST_MANAGE(PermissionGroup.FINANCE),
    REPORTS_VIEW(PermissionGroup.FINANCE),

    // Medical work
    MEDICAL_HISTORY_VIEW(PermissionGroup.MEDICAL),
    CONSULTATION_WRITE(PermissionGroup.MEDICAL),
    SERVICE_ORDER(PermissionGroup.MEDICAL),
    LAB_WORK(PermissionGroup.MEDICAL),
    LAB_TEMPLATES_MANAGE(PermissionGroup.MEDICAL),
    INPATIENT_WORK(PermissionGroup.MEDICAL),
    OPERATIONS_WORK(PermissionGroup.MEDICAL),
}

enum class PermissionGroup { ADMINISTRATION, REGISTRATION, CASHIER, FINANCE, MEDICAL }

/** Constants for `@PreAuthorize`. */
object Permissions {
    const val HAS_STAFF_VIEW = "hasAuthority('STAFF_VIEW')"
    const val HAS_STAFF_MANAGE = "hasAuthority('STAFF_MANAGE')"
    const val HAS_ROLE_MANAGE = "hasAuthority('ROLE_MANAGE')"
    const val HAS_SETTINGS_MANAGE = "hasAuthority('SETTINGS_MANAGE')"
    const val HAS_AUDIT_VIEW = "hasAuthority('AUDIT_VIEW')"
    const val HAS_LICENSE_MANAGE = "hasAuthority('LICENSE_MANAGE')"
    const val HAS_DATA_IMPORT = "hasAuthority('DATA_IMPORT')"
    const val HAS_PATIENT_VIEW = "hasAuthority('PATIENT_VIEW')"
    const val HAS_PATIENT_EDIT = "hasAuthority('PATIENT_EDIT')"
    const val HAS_PATIENT_DELETE = "hasAuthority('PATIENT_DELETE')"
}
