package uz.medclinic.vendor.tenancy.api

import java.util.UUID

/** Lifecycle shared by clinics and branches. */
enum class TenantStatus {
    ACTIVE,
    SUSPENDED,
    ARCHIVED,
    ;

    fun canTransitionTo(target: TenantStatus): Boolean = when (this) {
        ACTIVE -> target == SUSPENDED || target == ARCHIVED
        SUSPENDED -> target == ACTIVE || target == ARCHIVED
        ARCHIVED -> target == ACTIVE
    }
}

data class ClinicSummary(val id: UUID, val code: String, val name: String, val status: TenantStatus)

/**
 * [effectiveStatus] combines branch and clinic: a branch of a suspended/archived clinic is effectively
 * suspended/archived too.
 */
data class BranchSummary(
    val id: UUID,
    val clinicId: UUID,
    val code: String,
    val name: String,
    val status: TenantStatus,
    val clinic: ClinicSummary,
) {
    val effectiveStatus: TenantStatus
        get() = listOf(status, clinic.status).maxBy { it.ordinal }

    val operational: Boolean get() = effectiveStatus == TenantStatus.ACTIVE
}

/** Read access for other modules. Deleted clinics/branches are not returned. */
interface TenancyQueries {
    fun findClinic(id: UUID): ClinicSummary?
    fun findBranch(id: UUID): BranchSummary?
    fun findBranches(ids: Collection<UUID>): Map<UUID, BranchSummary>

    /** All non-deleted, non-archived branches of non-archived clinics (vendor scale: hundreds of rows). */
    fun listLiveBranches(): List<BranchSummary>

    /** Non-deleted, non-archived branches of a clinic. */
    fun countLiveBranches(clinicId: UUID): Int
}

/** Published inside the deleting transaction; listeners must not fail for unrelated reasons. */
data class ClinicDeleted(val clinicId: UUID)

data class BranchDeleted(val clinicId: UUID, val branchId: UUID)
