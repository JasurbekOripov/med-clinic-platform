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
    AGENT_MANAGE(PermissionGroup.FINANCE),
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
    const val HAS_VISIT_CREATE = "hasAuthority('VISIT_CREATE')"
    const val HAS_VISIT_EDIT_UNPAID = "hasAuthority('VISIT_EDIT_UNPAID')"
    const val HAS_EMERGENCY_ALLOW = "hasAuthority('EMERGENCY_ALLOW')"
    const val HAS_PAYMENT_ACCEPT = "hasAuthority('PAYMENT_ACCEPT')"
    const val HAS_SHIFT_MANAGE = "hasAuthority('SHIFT_MANAGE')"
    const val HAS_REFUND = "hasAuthority('REFUND')"
    const val HAS_DEBT_VIEW = "hasAuthority('DEBT_VIEW')"
    const val HAS_REPORTS_VIEW = "hasAuthority('REPORTS_VIEW')"

    /** Reprinting receipts and room slips: the cashier, and Reception when the patient lost a slip. */
    const val HAS_RECEIPT_REPRINT = "hasAnyAuthority('PAYMENT_ACCEPT', 'VISIT_CREATE')"
    const val HAS_PRICE_LIST_MANAGE = "hasAuthority('PRICE_LIST_MANAGE')"

    const val HAS_AGENT_MANAGE = "hasAuthority('AGENT_MANAGE')"

    /** Agents list: who manages agents, and Reception, who records which agent referred a visit. */
    const val HAS_AGENT_READ = "hasAnyAuthority('AGENT_MANAGE', 'VISIT_CREATE')"

    /** Staff who print documents with the clinic's letterhead (receipts now; results and letters later). */
    const val HAS_PRINTING =
        "hasAnyAuthority('SETTINGS_MANAGE', 'PAYMENT_ACCEPT', 'VISIT_CREATE', 'RESULTS_HANDOVER', 'CONSULTATION_WRITE', 'LAB_WORK')"

    /** Queue lists: reception, doctors and lab (their own rooms), and the Admin who sets queues up. */
    const val HAS_QUEUE_READ = "hasAnyAuthority('QUEUE_MANAGE', 'CONSULTATION_WRITE', 'LAB_WORK', 'SETTINGS_MANAGE')"

    const val HAS_CONSULTATION_WRITE = "hasAuthority('CONSULTATION_WRITE')"

    /** Reading conclusions: doctors and nurses (history), Reception handing ready results to the patient. */
    const val HAS_CONCLUSION_READ = "hasAnyAuthority('CONSULTATION_WRITE', 'MEDICAL_HISTORY_VIEW', 'RESULTS_HANDOVER')"

    const val HAS_LAB_TEMPLATES_MANAGE = "hasAuthority('LAB_TEMPLATES_MANAGE')"
    const val HAS_LAB_READ = "hasAnyAuthority('LAB_WORK', 'LAB_TEMPLATES_MANAGE')"
    const val HAS_LAB_WORK = "hasAuthority('LAB_WORK')"

    /** Lab results: the lab, doctors and nurses (history), Reception printing ready results for the patient. */
    const val HAS_LAB_RESULT_READ = "hasAnyAuthority('LAB_WORK', 'CONSULTATION_WRITE', 'MEDICAL_HISTORY_VIEW', 'RESULTS_HANDOVER')"

    /**
     * Reading the price list and discount types: who manages it, who orders or takes money for services, and the lab
     * linking its result forms to services.
     */
    const val HAS_CATALOG_READ = "hasAnyAuthority('PRICE_LIST_MANAGE', 'VISIT_CREATE', 'PAYMENT_ACCEPT', 'LAB_TEMPLATES_MANAGE')"
}
