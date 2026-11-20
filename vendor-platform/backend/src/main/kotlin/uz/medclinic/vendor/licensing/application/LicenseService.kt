package uz.medclinic.vendor.licensing.application

import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.common.Ids
import uz.medclinic.license.LicensePayload
import uz.medclinic.license.SignedLicense
import uz.medclinic.vendor.audit.api.AuditEvent
import uz.medclinic.vendor.audit.api.AuditRecorder
import uz.medclinic.vendor.licensing.api.CurrentLicense
import uz.medclinic.vendor.licensing.api.LicenseQueries
import uz.medclinic.vendor.licensing.api.LicenseRecordState
import uz.medclinic.vendor.licensing.domain.License
import uz.medclinic.vendor.licensing.domain.LicenseRepository
import uz.medclinic.vendor.licensing.domain.LicenseTerms
import uz.medclinic.vendor.licensing.infrastructure.SigningKeys
import uz.medclinic.vendor.shared.domain.DomainException
import uz.medclinic.vendor.shared.domain.NotFoundException
import uz.medclinic.vendor.shared.domain.Text
import uz.medclinic.vendor.shared.domain.ValidationException
import uz.medclinic.vendor.shared.domain.VersionConflictException
import uz.medclinic.vendor.shared.security.ActorType
import uz.medclinic.vendor.shared.security.CurrentActor
import uz.medclinic.vendor.tenancy.api.ClinicDeleted
import uz.medclinic.vendor.tenancy.api.ClinicSummary
import uz.medclinic.vendor.tenancy.api.TenancyQueries
import uz.medclinic.vendor.tenancy.api.TenantStatus
import java.time.Clock
import java.util.UUID

data class LicenseFile(val fileName: String, val bytes: ByteArray)

@Service
class LicenseService(
    private val licenses: LicenseRepository,
    private val tenancy: TenancyQueries,
    private val keys: SigningKeys,
    private val audit: AuditRecorder,
    private val clock: Clock,
) : LicenseQueries {
    @Transactional(readOnly = true)
    fun list(clinicId: UUID): List<License> {
        requireClinic(clinicId)
        return licenses.findByClinic(clinicId)
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): License = licenses.findById(id) ?: throw NotFoundException(ENTITY, id)

    @Transactional
    fun issue(clinicId: UUID, terms: LicenseTerms): License {
        val clinic = requireLicensableClinic(clinicId)
        if (licenses.findCurrent(clinicId) != null) throw DomainException("license.current_exists", DomainException.Kind.CONFLICT)
        requireBranchCapacity(clinicId, terms)
        val license = sign(clinic, terms, renewedFrom = null)
        licenses.insert(license)
        audit.record(AuditEvent("license.issued", ENTITY, license.id, clinicId, after = snapshot(license)))
        return license
    }

    /** Replaces the current license with a new one (new id and serial); the old one becomes SUPERSEDED. */
    @Transactional
    fun renew(id: UUID, version: Int, terms: LicenseTerms): License {
        val old = load(id, version)
        if (!old.current) throw DomainException("license.not_current")
        val clinic = requireLicensableClinic(old.clinicId)
        requireBranchCapacity(old.clinicId, terms)
        val superseded = licenses.markSuperseded(old.id, clock.instant(), version)
        val renewed = sign(clinic, terms, renewedFrom = old.id)
        licenses.insert(renewed)
        licenses.linkSuccessor(superseded.id, renewed.id)
        audit.record(AuditEvent("license.renewed", ENTITY, renewed.id, old.clinicId, before = snapshot(old), after = snapshot(renewed)))
        return renewed
    }

    @Transactional
    fun revoke(id: UUID, version: Int, reason: String?): License {
        val cleanReason = Text.cleanMultiline(reason)
        if (cleanReason == null || cleanReason.length !in 3..500) {
            throw ValidationException("reason", "validation.size", mapOf("min" to 3, "max" to 500))
        }
        val current = load(id, version)
        val revoked = licenses.update(current.revoked(CurrentActor.staffId(), cleanReason, clock.instant()))
        audit.record(AuditEvent("license.revoked", ENTITY, id, current.clinicId, after = mapOf("reason" to cleanReason)))
        return revoked
    }

    @Transactional
    fun download(id: UUID): LicenseFile {
        val license = get(id)
        val clinicCode = tenancy.findClinic(license.clinicId)?.code ?: "clinic"
        audit.record(AuditEvent("license.downloaded", ENTITY, id, license.clinicId))
        return LicenseFile("license-$clinicCode-${license.serial}.lic", fileBytes(license))
    }

    fun publicKeys(): Map<String, String> = mapOf(keys.keyId to uz.medclinic.license.Ed25519Keys.publicKeyToBase64(keys.publicKey))

    /** A deleted clinic must not keep a usable license of record. */
    @EventListener
    fun onClinicDeleted(event: ClinicDeleted) {
        val current = licenses.findCurrent(event.clinicId) ?: return
        val actor = CurrentActor.get()
        val by = actor.actorId?.takeIf { actor.actorType == ActorType.STAFF } ?: SYSTEM_ID
        licenses.update(current.revoked(by, "clinic_deleted", clock.instant()))
        audit.record(AuditEvent("license.revoked", ENTITY, current.id, event.clinicId, after = mapOf("reason" to "clinic_deleted")))
    }

    // ---- LicenseQueries (used by the devices module) ----

    @Transactional(readOnly = true)
    override fun currentLicense(clinicId: UUID): CurrentLicense? =
        licenses.findCurrent(clinicId)?.let { CurrentLicense(it.id, it.terms.maxBranches, fileBytes(it)) }

    @Transactional(readOnly = true)
    override fun stateOf(clinicId: UUID, licenseId: UUID): LicenseRecordState {
        val license = licenses.findById(licenseId)?.takeIf { it.clinicId == clinicId } ?: return LicenseRecordState.UNKNOWN
        return when {
            license.revokedAt != null -> LicenseRecordState.REVOKED
            license.supersededAt != null -> LicenseRecordState.SUPERSEDED
            else -> LicenseRecordState.CURRENT
        }
    }

    private fun sign(clinic: ClinicSummary, terms: LicenseTerms, renewedFrom: UUID?): License {
        val now = clock.instant()
        val id = Ids.newId()
        val serial = licenses.nextSerial()
        val payload = LicensePayload(
            licenseId = id, serial = serial, clinicId = clinic.id, clinicCode = clinic.code, clinicName = clinic.name,
            plan = terms.plan, modules = terms.modules, maxUsers = terms.maxUsers, maxBranches = terms.maxBranches,
            validFrom = terms.validFrom, validUntil = terms.validUntil, expiryGraceDays = terms.expiryGraceDays, issuedAt = now,
        )
        val signed = keys.signer.sign(payload)
        return License(
            id = id, clinicId = clinic.id, serial = serial, terms = terms, issuedAt = now, issuedBy = CurrentActor.staffId(),
            keyId = signed.keyId, payload = signed.payloadBytes, signature = signed.signature, renewedFrom = renewedFrom,
            supersededAt = null, supersededBy = null, revokedAt = null, revokedBy = null, revokeReason = null, version = 0,
        )
    }

    private fun fileBytes(license: License): ByteArray = SignedLicense(license.keyId, license.payload, license.signature).fileBytes()

    private fun requireClinic(clinicId: UUID): ClinicSummary = tenancy.findClinic(clinicId) ?: throw NotFoundException("clinic", clinicId)

    private fun requireLicensableClinic(clinicId: UUID): ClinicSummary {
        val clinic = requireClinic(clinicId)
        if (clinic.status == TenantStatus.ARCHIVED) throw DomainException("clinic.archived")
        return clinic
    }

    private fun requireBranchCapacity(clinicId: UUID, terms: LicenseTerms) {
        val live = tenancy.countLiveBranches(clinicId)
        if (terms.maxBranches < live) {
            throw ValidationException("maxBranches", "validation.below_current_branches", mapOf("current" to live))
        }
    }

    private fun load(id: UUID, version: Int): License =
        get(id).also { if (it.version != version) throw VersionConflictException(ENTITY, id) }

    private fun snapshot(l: License) = mapOf(
        "serial" to l.serial, "plan" to l.terms.plan, "modules" to l.terms.modules, "maxUsers" to l.terms.maxUsers,
        "maxBranches" to l.terms.maxBranches, "startsOn" to l.terms.startsOn, "endsOn" to l.terms.endsOn,
        "expiryGraceDays" to l.terms.expiryGraceDays,
    )

    private companion object {
        const val ENTITY = "license"
        val SYSTEM_ID: UUID = UUID(0, 0)
    }
}
