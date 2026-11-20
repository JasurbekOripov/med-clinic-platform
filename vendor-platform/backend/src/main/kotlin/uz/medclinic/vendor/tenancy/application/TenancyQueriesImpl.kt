package uz.medclinic.vendor.tenancy.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.vendor.tenancy.api.BranchSummary
import uz.medclinic.vendor.tenancy.api.ClinicSummary
import uz.medclinic.vendor.tenancy.api.TenancyQueries
import uz.medclinic.vendor.tenancy.domain.Branch
import uz.medclinic.vendor.tenancy.domain.BranchRepository
import uz.medclinic.vendor.tenancy.domain.Clinic
import uz.medclinic.vendor.tenancy.domain.ClinicRepository
import java.util.UUID

@Service
@Transactional(readOnly = true)
class TenancyQueriesImpl(
    private val clinics: ClinicRepository,
    private val branches: BranchRepository,
) : TenancyQueries {
    override fun findClinic(id: UUID): ClinicSummary? = clinics.findById(id)?.let(::summary)

    override fun findBranch(id: UUID): BranchSummary? {
        val branch = branches.findById(id) ?: return null
        val clinic = clinics.findById(branch.clinicId) ?: return null
        return summary(branch, summary(clinic))
    }

    override fun findBranches(ids: Collection<UUID>): Map<UUID, BranchSummary> =
        summarise(branches.findByIds(ids)).associateBy { it.id }

    override fun listLiveBranches(): List<BranchSummary> = summarise(branches.findAllLive())

    private fun summarise(found: List<Branch>): List<BranchSummary> {
        val clinicSummaries = clinics.findByIds(found.map { it.clinicId }.distinct()).associate { it.id to summary(it) }
        return found.mapNotNull { b -> clinicSummaries[b.clinicId]?.let { summary(b, it) } }
    }

    override fun countLiveBranches(clinicId: UUID): Int = branches.countLive(clinicId)

    private fun summary(c: Clinic) = ClinicSummary(c.id, c.code, c.details.name, c.status)

    private fun summary(b: Branch, clinic: ClinicSummary) = BranchSummary(b.id, b.clinicId, b.code, b.details.name, b.status, clinic)
}
