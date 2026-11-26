package uz.medclinic.clinic.shared

import org.springframework.security.core.context.SecurityContextHolder

/**
 * The signed-in staff member's effective permissions, for rules that change *what* is returned rather than whether
 * the endpoint may be called (that is `@PreAuthorize`), e.g. hiding unpaid services from doctors.
 */
object CurrentPermissions {
    fun hasAny(vararg permissions: Permission): Boolean {
        val granted = SecurityContextHolder.getContext().authentication?.authorities?.mapTo(HashSet()) { it.authority } ?: return false
        return permissions.any { it.name in granted }
    }
}
