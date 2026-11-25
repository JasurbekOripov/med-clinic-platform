package uz.medclinic.clinic.identity.api

import java.util.UUID

/** Used by the first-run setup and by importers. */
interface IdentitySetup {
    /** Creates the role templates for this clinic (idempotent). */
    fun seedRoles()

    fun hasActiveAdmin(): Boolean

    /** First Admin (and optionally the vendor's support account); allowed only while no active Admin exists. */
    fun createFirstAdmin(fullName: String, login: String, password: String, vendorSupportPassword: String?): UUID
}

data class NewStaff(val login: String, val fullName: String, val position: String?, val phone: String?, val roleId: UUID)

data class CreatedStaff(val id: UUID, val login: String, val fullName: String, val temporaryPassword: String)

interface StaffDirectory {
    /** Normalised login, or null when it does not match the login rules. */
    fun normaliseLogin(raw: String): String?

    fun loginExists(login: String): Boolean

    fun fullNameExists(fullName: String): Boolean

    /** Role id by template code (e.g. `DOCTOR`) or by custom role name, case-insensitive; null if unknown. */
    fun findRoleId(codeOrName: String): UUID?

    /** Creates staff in the caller's transaction; each gets a one-time temporary password. */
    fun createAll(staff: List<NewStaff>): List<CreatedStaff>
}
