package uz.medclinic.vendor.tenancy.application

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.common.Ids
import uz.medclinic.vendor.audit.api.AuditEvent
import uz.medclinic.vendor.audit.api.AuditRecorder
import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.domain.NotFoundException
import uz.medclinic.platform.domain.Page
import uz.medclinic.platform.domain.PageQuery
import uz.medclinic.platform.domain.Text
import uz.medclinic.platform.domain.VersionConflictException
import uz.medclinic.vendor.tenancy.api.BranchDeleted
import uz.medclinic.vendor.tenancy.api.ClinicDeleted
import uz.medclinic.vendor.tenancy.api.TenantStatus
import uz.medclinic.vendor.tenancy.domain.Branch
import uz.medclinic.vendor.tenancy.domain.BranchDetails
import uz.medclinic.vendor.tenancy.domain.BranchRepository
import uz.medclinic.vendor.tenancy.domain.Clinic
import uz.medclinic.vendor.tenancy.domain.ClinicDetails
import uz.medclinic.vendor.tenancy.domain.ClinicFilter
import uz.medclinic.vendor.tenancy.domain.ClinicListItem
import uz.medclinic.vendor.tenancy.domain.ClinicRepository
import java.time.Clock
import java.util.UUID

@Service
class TenancyService(
    private val clinics: ClinicRepository,
    private val branches: BranchRepository,
    private val audit: AuditRecorder,
    private val events: ApplicationEventPublisher,
    private val clock: Clock,
) {
    // ---- clinics ----

    @Transactional(readOnly = true)
    fun searchClinics(filter: ClinicFilter, page: PageQuery): Page<ClinicListItem> =
        clinics.search(filter.copy(query = Text.clean(filter.query)), page)

    @Transactional(readOnly = true)
    fun getClinic(id: UUID): Clinic = clinics.findById(id) ?: throw NotFoundException(CLINIC, id)

    @Transactional
    fun createClinic(details: ClinicDetails): Clinic {
        val now = clock.instant()
        val clinic = clinics.insert(Clinic(Ids.newId(), code = "", details, TenantStatus.ACTIVE, now, now, null, 0))
        audit.record(AuditEvent("clinic.created", CLINIC, clinic.id, clinic.id, after = snapshot(clinic)))
        return clinic
    }

    @Transactional
    fun updateClinic(id: UUID, version: Int, details: ClinicDetails): Clinic {
        val current = loadClinic(id, version)
        val updated = clinics.update(current.withDetails(details, clock.instant()))
        audit.record(AuditEvent("clinic.updated", CLINIC, id, id, before = snapshot(current), after = snapshot(updated)))
        return updated
    }

    @Transactional
    fun changeClinicStatus(id: UUID, version: Int, target: TenantStatus): Clinic {
        val current = loadClinic(id, version)
        if (current.status == target) return current
        val updated = clinics.update(current.withStatus(target, clock.instant()))
        audit.record(AuditEvent("clinic.status_changed", CLINIC, id, id, before = mapOf("status" to current.status), after = mapOf("status" to target)))
        return updated
    }

    /** Soft-deletes an archived clinic together with its branches. */
    @Transactional
    fun deleteClinic(id: UUID, version: Int) {
        val current = loadClinic(id, version)
        val now = clock.instant()
        val deleted = current.deleted(now)
        branches.findByClinic(id, includeArchived = true).forEach { branch ->
            branches.update(branch.copy(deletedAt = now, updatedAt = now))
            events.publishEvent(BranchDeleted(id, branch.id))
        }
        clinics.update(deleted)
        audit.record(AuditEvent("clinic.deleted", CLINIC, id, id, before = snapshot(current)))
        events.publishEvent(ClinicDeleted(id))
    }

    // ---- branches ----

    @Transactional(readOnly = true)
    fun listBranches(clinicId: UUID, includeArchived: Boolean): List<Branch> {
        getClinic(clinicId)
        return branches.findByClinic(clinicId, includeArchived)
    }

    @Transactional(readOnly = true)
    fun getBranch(id: UUID): Branch = branches.findById(id) ?: throw NotFoundException(BRANCH, id)

    @Transactional
    fun createBranch(clinicId: UUID, details: BranchDetails): Branch {
        val clinic = getClinic(clinicId)
        if (clinic.archived) throw DomainException("clinic.archived")
        val now = clock.instant()
        val code = Branch.code(clinic.code, clinics.nextBranchNumber(clinicId))
        val branch = Branch(Ids.newId(), clinicId, code, details, TenantStatus.ACTIVE, now, now, null, 0)
        branches.insert(branch)
        audit.record(AuditEvent("branch.created", BRANCH, branch.id, clinicId, after = snapshot(branch)))
        return branch
    }

    @Transactional
    fun updateBranch(id: UUID, version: Int, details: BranchDetails): Branch {
        val current = loadBranch(id, version)
        requireClinicNotArchived(current.clinicId)
        val updated = branches.update(current.withDetails(details, clock.instant()))
        audit.record(AuditEvent("branch.updated", BRANCH, id, current.clinicId, before = snapshot(current), after = snapshot(updated)))
        return updated
    }

    @Transactional
    fun changeBranchStatus(id: UUID, version: Int, target: TenantStatus): Branch {
        val current = loadBranch(id, version)
        if (current.status == target) return current
        requireClinicNotArchived(current.clinicId)
        val updated = branches.update(current.withStatus(target, clock.instant()))
        audit.record(
            AuditEvent("branch.status_changed", BRANCH, id, current.clinicId, before = mapOf("status" to current.status), after = mapOf("status" to target)),
        )
        return updated
    }

    @Transactional
    fun deleteBranch(id: UUID, version: Int) {
        val current = loadBranch(id, version)
        branches.update(current.deleted(clock.instant()))
        audit.record(AuditEvent("branch.deleted", BRANCH, id, current.clinicId, before = snapshot(current)))
        events.publishEvent(BranchDeleted(current.clinicId, id))
    }

    private fun requireClinicNotArchived(clinicId: UUID) {
        if (getClinic(clinicId).archived) throw DomainException("clinic.archived")
    }

    private fun loadClinic(id: UUID, version: Int): Clinic =
        getClinic(id).also { if (it.version != version) throw VersionConflictException(CLINIC, id) }

    private fun loadBranch(id: UUID, version: Int): Branch =
        getBranch(id).also { if (it.version != version) throw VersionConflictException(BRANCH, id) }

    private fun snapshot(c: Clinic) = mapOf(
        "code" to c.code, "name" to c.details.name, "legalName" to c.details.legalName, "ownerName" to c.details.ownerName,
        "phone" to c.details.phone.e164, "address" to c.details.address, "region" to c.details.region, "notes" to c.details.notes,
        "status" to c.status,
    )

    private fun snapshot(b: Branch) = mapOf(
        "code" to b.code, "name" to b.details.name, "address" to b.details.address, "phone" to b.details.phone?.e164, "status" to b.status,
    )

    private companion object {
        const val CLINIC = "clinic"
        const val BRANCH = "branch"
    }
}
