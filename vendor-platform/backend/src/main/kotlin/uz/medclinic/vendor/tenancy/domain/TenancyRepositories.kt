package uz.medclinic.vendor.tenancy.domain

import uz.medclinic.vendor.shared.domain.Page
import uz.medclinic.vendor.shared.domain.PageQuery
import uz.medclinic.vendor.tenancy.api.TenantStatus
import java.util.UUID

data class ClinicFilter(val query: String? = null, val status: TenantStatus? = null, val region: Region? = null)

data class ClinicListItem(val clinic: Clinic, val branchCount: Int)

interface ClinicRepository {
    /** Assigns the next human code (`CL-0001`) atomically and returns the stored clinic. */
    fun insert(clinic: Clinic): Clinic
    fun findById(id: UUID): Clinic?
    fun findByIds(ids: Collection<UUID>): List<Clinic>
    fun search(filter: ClinicFilter, page: PageQuery): Page<ClinicListItem>
    fun update(clinic: Clinic): Clinic

    /** Atomically reserves the next branch number of a clinic. */
    fun nextBranchNumber(clinicId: UUID): Int
}

interface BranchRepository {
    fun insert(branch: Branch)
    fun findById(id: UUID): Branch?
    fun findByClinic(clinicId: UUID, includeArchived: Boolean): List<Branch>
    fun findByIds(ids: Collection<UUID>): List<Branch>
    fun findAllLive(): List<Branch>
    fun update(branch: Branch): Branch
    fun countLive(clinicId: UUID): Int
}
