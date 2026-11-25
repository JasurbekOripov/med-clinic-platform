package uz.medclinic.vendor.devices.application

import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.common.Ids
import uz.medclinic.vendor.audit.api.AuditEvent
import uz.medclinic.vendor.audit.api.AuditRecorder
import uz.medclinic.vendor.devices.api.AuthenticatedDevice
import uz.medclinic.vendor.devices.api.DeviceInfo
import uz.medclinic.vendor.devices.api.DeviceQueries
import uz.medclinic.vendor.devices.domain.Device
import uz.medclinic.vendor.devices.domain.DeviceRepository
import uz.medclinic.vendor.devices.domain.DeviceSecrets
import uz.medclinic.vendor.devices.domain.DeviceStatus
import uz.medclinic.vendor.devices.domain.PairingCode
import uz.medclinic.vendor.devices.domain.PairingCodeRepository
import uz.medclinic.vendor.devices.domain.PairingCodeState
import uz.medclinic.vendor.licensing.api.LicenseQueries
import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.domain.FieldViolation
import uz.medclinic.platform.domain.NotFoundException
import uz.medclinic.platform.domain.Text
import uz.medclinic.platform.domain.ValidationException
import uz.medclinic.platform.domain.VersionConflictException
import uz.medclinic.platform.security.CurrentActor
import uz.medclinic.vendor.tenancy.api.BranchDeleted
import uz.medclinic.vendor.tenancy.api.ClinicDeleted
import uz.medclinic.vendor.tenancy.api.TenancyQueries
import uz.medclinic.vendorprotocol.PairingClaimRequest
import uz.medclinic.vendorprotocol.PairingClaimResponse
import java.time.Clock
import java.util.UUID

/** Plain code returned exactly once, at creation. */
data class CreatedPairingCode(val code: PairingCode, val plainCode: String)

@Service
class PairingService(
    private val codes: PairingCodeRepository,
    private val devices: DeviceRepository,
    private val tenancy: TenancyQueries,
    private val licenses: LicenseQueries,
    private val audit: AuditRecorder,
    private val properties: DeviceProperties,
    private val clock: Clock,
) : DeviceQueries {
    // ---- staff side ----

    @Transactional
    fun createCode(branchId: UUID): CreatedPairingCode {
        val branch = tenancy.findBranch(branchId) ?: throw NotFoundException("branch", branchId)
        if (!branch.operational) throw DomainException("pairing.branch_not_active")
        val now = clock.instant()
        val plain = DeviceSecrets.newPairingCode()
        val code = PairingCode(
            id = Ids.newId(), clinicId = branch.clinicId, branchId = branchId,
            codeHash = DeviceSecrets.sha256(DeviceSecrets.normalisePairingCode(plain)!!),
            expiresAt = now.plus(properties.pairingCodeTtl), createdBy = CurrentActor.staffId(), createdAt = now,
            claimedAt = null, claimedDeviceId = null, cancelledAt = null,
        )
        codes.insert(code)
        audit.record(AuditEvent("pairing_code.created", "pairing_code", code.id, branch.clinicId, after = mapOf("branchId" to branchId, "expiresAt" to code.expiresAt)))
        return CreatedPairingCode(code, plain)
    }

    @Transactional(readOnly = true)
    fun listCodes(branchId: UUID): List<PairingCode> {
        tenancy.findBranch(branchId) ?: throw NotFoundException("branch", branchId)
        return codes.findByBranch(branchId, limit = 20)
    }

    @Transactional
    fun cancelCode(id: UUID) {
        val code = codes.findById(id) ?: throw NotFoundException("pairing_code", id)
        if (!codes.cancel(id, clock.instant())) throw DomainException("pairing.code_not_open")
        audit.record(AuditEvent("pairing_code.cancelled", "pairing_code", id, code.clinicId))
    }

    @Transactional(readOnly = true)
    fun listDevices(branchId: UUID): List<Device> {
        tenancy.findBranch(branchId) ?: throw NotFoundException("branch", branchId)
        return devices.findByBranch(branchId)
    }

    @Transactional
    fun revokeDevice(id: UUID, version: Int, reason: String?): Device {
        val device = devices.findById(id) ?: throw NotFoundException("device", id)
        if (device.version != version) throw VersionConflictException("device", id)
        if (device.status == DeviceStatus.REVOKED) throw DomainException("device.already_revoked")
        val cleanReason = Text.clean(reason) ?: "revoked_by_staff"
        val revoked = devices.revoke(id, cleanReason.take(200), clock.instant(), version)
        audit.record(AuditEvent("device.revoked", "device", id, device.clinicId, after = mapOf("reason" to cleanReason)))
        return revoked
    }

    // ---- device side ----

    /**
     * Exchanges a one-time code for a device credential. Every code problem yields the same
     * `pairing.invalid_code`, so callers cannot probe which codes exist.
     */
    @Transactional
    fun claim(request: PairingClaimRequest, ip: String?): PairingClaimResponse {
        val hostname = field("hostname", request.hostname, 255)
        val os = field("os", request.os, 100)
        val appVersion = field("appVersion", request.appVersion, 50)
        val violations = listOfNotNull(hostname.second, os.second, appVersion.second)
        if (violations.isNotEmpty()) throw ValidationException(violations)

        val now = clock.instant()
        val normalised = DeviceSecrets.normalisePairingCode(request.code) ?: throw invalidCode()
        val code = codes.findByHashForUpdate(DeviceSecrets.sha256(normalised)) ?: throw invalidCode()
        if (code.state(now) != PairingCodeState.OPEN) throw invalidCode()
        val branch = tenancy.findBranch(code.branchId)?.takeIf { it.operational } ?: throw invalidCode()

        val license = licenses.currentLicense(branch.clinicId)
        if (license != null) {
            val pairedBranches = devices.findActiveByClinic(branch.clinicId).map { it.branchId }.toSet() + branch.id
            if (pairedBranches.size > license.maxBranches) {
                throw DomainException("pairing.branch_limit_reached", params = mapOf("maxBranches" to license.maxBranches))
            }
        }

        devices.findActiveByBranch(branch.id)?.let { previous ->
            devices.revoke(previous.id, "replaced", now, null)
            audit.record(AuditEvent("device.revoked", "device", previous.id, branch.clinicId, after = mapOf("reason" to "replaced")))
        }
        val token = DeviceSecrets.newDeviceToken()
        val device = Device(
            id = Ids.newId(), clinicId = branch.clinicId, branchId = branch.id, tokenHash = DeviceSecrets.sha256(token),
            status = DeviceStatus.ACTIVE, hostname = hostname.first!!, os = os.first!!, appVersion = appVersion.first!!,
            pairedAt = now, revokedAt = null, revokeReason = null, lastSeenAt = null, lastIp = ip, version = 0,
        )
        devices.insert(device)
        codes.markClaimed(code.id, device.id, now)
        audit.record(
            AuditEvent(
                "device.paired", "device", device.id, branch.clinicId,
                after = mapOf("branchId" to branch.id, "hostname" to device.hostname, "os" to device.os, "appVersion" to device.appVersion),
                actor = AuthenticatedDevice(device.id, device.clinicId, device.branchId, device.hostname),
            ),
        )
        return PairingClaimResponse(
            deviceId = device.id, clinicId = branch.clinicId, clinicCode = branch.clinic.code, branchId = branch.id,
            branchCode = branch.code, deviceToken = token, heartbeatIntervalSeconds = properties.heartbeatInterval.toSeconds().toInt(),
            licenseFile = license?.fileBytes?.toString(Charsets.UTF_8),
            clinicName = branch.clinic.name,
            branchName = branch.name,
        )
    }

    @Transactional(readOnly = true)
    fun currentLicenseFile(device: AuthenticatedDevice): ByteArray =
        licenses.currentLicense(device.clinicId)?.fileBytes ?: throw DomainException("license.none", DomainException.Kind.NOT_FOUND)

    // ---- reactions to tenancy changes ----

    @EventListener
    fun onBranchDeleted(event: BranchDeleted) = retireBranch(event.branchId, event.clinicId)

    @EventListener
    fun onClinicDeleted(event: ClinicDeleted) {
        devices.findActiveByClinic(event.clinicId).forEach { retireBranch(it.branchId, it.clinicId) }
    }

    private fun retireBranch(branchId: UUID, clinicId: UUID) {
        val now = clock.instant()
        codes.cancelOpenForBranch(branchId, now)
        devices.findActiveByBranch(branchId)?.let {
            devices.revoke(it.id, "branch_deleted", now, null)
            audit.record(AuditEvent("device.revoked", "device", it.id, clinicId, after = mapOf("reason" to "branch_deleted")))
        }
    }

    // ---- DeviceQueries ----

    @Transactional(readOnly = true)
    override fun activeDevices(): List<DeviceInfo> = devices.findActive().map {
        DeviceInfo(it.id, it.clinicId, it.branchId, it.hostname, it.os, it.appVersion, it.pairedAt, it.lastSeenAt)
    }

    @Transactional(readOnly = true)
    override fun isActive(deviceId: UUID): Boolean = devices.findById(deviceId)?.status == DeviceStatus.ACTIVE

    override fun heartbeatIntervalSeconds(): Int = properties.heartbeatInterval.toSeconds().toInt()

    private fun invalidCode() = DomainException("pairing.invalid_code", DomainException.Kind.UNAUTHORIZED)

    private fun field(name: String, raw: String?, max: Int): Pair<String?, FieldViolation?> {
        val value = Text.clean(raw)
        return when {
            value == null -> null to FieldViolation(name, "validation.not_blank")
            value.length > max -> null to FieldViolation(name, "validation.size", mapOf("max" to max))
            else -> value to null
        }
    }
}
