package uz.medclinic.vendor.shared.security

/**
 * Permission vocabulary of the Vendor Platform. Endpoints check permissions (never roles) via
 * `@PreAuthorize(Permissions.HAS_…)`. The role → permission mapping lives in the identity module.
 */
enum class Permission {
    CLINIC_VIEW,
    CLINIC_EDIT,
    CLINIC_STATUS,
    LICENSE_VIEW,
    LICENSE_MANAGE,
    DEVICE_PAIR,
    DEVICE_REVOKE,
    MONITORING_VIEW,
    STAFF_MANAGE,
    AUDIT_VIEW,
}

object Permissions {
    const val HAS_CLINIC_VIEW = "hasAuthority('CLINIC_VIEW')"
    const val HAS_CLINIC_EDIT = "hasAuthority('CLINIC_EDIT')"
    const val HAS_CLINIC_STATUS = "hasAuthority('CLINIC_STATUS')"
    const val HAS_LICENSE_VIEW = "hasAuthority('LICENSE_VIEW')"
    const val HAS_LICENSE_MANAGE = "hasAuthority('LICENSE_MANAGE')"
    const val HAS_DEVICE_PAIR = "hasAuthority('DEVICE_PAIR')"
    const val HAS_DEVICE_REVOKE = "hasAuthority('DEVICE_REVOKE')"
    const val HAS_MONITORING_VIEW = "hasAuthority('MONITORING_VIEW')"
    const val HAS_STAFF_MANAGE = "hasAuthority('STAFF_MANAGE')"
    const val HAS_AUDIT_VIEW = "hasAuthority('AUDIT_VIEW')"
}
