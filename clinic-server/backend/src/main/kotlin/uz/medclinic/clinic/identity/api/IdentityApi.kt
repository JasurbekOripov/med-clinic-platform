package uz.medclinic.clinic.identity.api

import uz.medclinic.clinic.shared.Permission
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

data class StaffRef(val id: UUID, val fullName: String)

interface StaffDirectory {
    /**
     * Active staff of non-system roles who effectively hold [permission] (role plus personal grants minus denials),
     * e.g. doctors = `CONSULTATION_WRITE`. Admin and Vendor support accounts are not listed: a doctor who is also
     * the owner works under a separate doctor account.
     */
    fun activeWith(permission: Permission): List<StaffRef>

    /** All active staff (e.g. who received cash from the drawer), sorted by name. */
    fun active(): List<StaffRef>

    /** Any staff member (also deactivated ones), e.g. for the cashier's name on a receipt. */
    fun find(id: UUID): StaffRef?

    /** Signature image (bytes, content type) for printed documents, or null when none was uploaded. */
    fun signature(id: UUID): Pair<ByteArray, String>?

    /** Normalised login, or null when it does not match the login rules. */
    fun normaliseLogin(raw: String): String?

    fun loginExists(login: String): Boolean

    fun fullNameExists(fullName: String): Boolean

    /** Role id by template code (e.g. `DOCTOR`) or by custom role name, case-insensitive; null if unknown. */
    fun findRoleId(codeOrName: String): UUID?

    /** Creates staff in the caller's transaction; each gets a one-time temporary password. */
    fun createAll(staff: List<NewStaff>): List<CreatedStaff>
}
