package uz.medclinic.vendor.identity.domain

import uz.medclinic.vendor.shared.domain.Page
import uz.medclinic.vendor.shared.domain.PageQuery
import java.time.Instant
import java.util.UUID

interface StaffRepository {
    fun findById(id: UUID): StaffAccount?
    fun findByLogin(login: String): StaffAccount?
    fun search(query: String?, includeInactive: Boolean, page: PageQuery): Page<StaffAccount>
    fun count(): Long
    fun countActiveOwners(): Long

    /** Fails with [uz.medclinic.vendor.shared.domain.DomainException] `staff.login_taken` on duplicates. */
    fun insert(staff: StaffAccount)

    /** Optimistic update: [StaffAccount.version] must match the stored row; returns the new version. */
    fun update(staff: StaffAccount): StaffAccount
    fun recordLogin(id: UUID, at: Instant)

    /** Serialises concurrent owner changes (last-owner rule) within the current transaction. */
    fun lockOwners()
}
