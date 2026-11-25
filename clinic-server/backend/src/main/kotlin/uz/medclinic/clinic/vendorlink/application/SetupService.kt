package uz.medclinic.clinic.vendorlink.application

import org.slf4j.LoggerFactory
import org.springframework.boot.info.BuildProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import uz.medclinic.clinic.audit.api.AuditEvent
import uz.medclinic.clinic.audit.api.AuditRecorder
import uz.medclinic.clinic.identity.api.IdentitySetup
import uz.medclinic.clinic.settings.api.SettingsSetup
import uz.medclinic.clinic.vendorlink.VendorLinkProperties
import uz.medclinic.clinic.vendorlink.infrastructure.Installation
import uz.medclinic.clinic.vendorlink.infrastructure.JdbcInstallationRepository
import uz.medclinic.clinic.vendorlink.infrastructure.TokenCipher
import uz.medclinic.clinic.vendorlink.infrastructure.VendorClient
import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.domain.Text
import uz.medclinic.platform.domain.ValidationException
import uz.medclinic.platform.security.ActorType
import uz.medclinic.platform.security.SimpleActor
import uz.medclinic.vendorprotocol.PairingClaimRequest
import java.net.InetAddress
import java.net.URI
import java.time.Clock
import java.util.UUID

data class SetupStatus(val paired: Boolean, val hasAdmin: Boolean, val clinicCode: String?, val branchCode: String?) {
    val completed: Boolean get() = paired && hasAdmin
}

/**
 * First run on a new clinic PC: (1) pair with the Vendor Platform using the one-time code, which tells this
 * server which clinic and branch it is and brings the license; (2) create the first Admin account.
 * Both steps refuse once done, so the open setup endpoints cannot be abused later.
 */
@Service
class SetupService(
    private val repository: JdbcInstallationRepository,
    private val installation: InstallationService,
    private val licenses: LicenseService,
    private val client: VendorClient,
    private val cipher: TokenCipher,
    private val identity: IdentitySetup,
    private val settings: SettingsSetup,
    private val audit: AuditRecorder,
    private val properties: VendorLinkProperties,
    private val transactions: TransactionTemplate,
    private val build: BuildProperties?,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun status(): SetupStatus {
        val current = installation.installation()
        return SetupStatus(current != null, current != null && identity.hasActiveAdmin(), current?.clinicCode, current?.branchCode)
    }

    fun pair(vendorUrl: String?, code: String?): SetupStatus {
        if (installation.installation() != null) throw DomainException("setup.already_paired", DomainException.Kind.CONFLICT)
        val url = validateUrl(vendorUrl)
        val pairingCode = Text.clean(code) ?: throw ValidationException("code", "validation.not_blank")

        // Network call outside any transaction: the vendor may be slow.
        val response = client.claim(
            url,
            PairingClaimRequest(pairingCode, hostname(), "${System.getProperty("os.name")} ${System.getProperty("os.version")}", appVersion()),
        )
        transactions.executeWithoutResult {
            val inserted = repository.insert(
                Installation(
                    response.clinicId, response.branchId, response.clinicCode, response.branchCode, url, response.deviceId,
                    cipher.encrypt(response.deviceToken), clock.instant(),
                ),
            )
            if (!inserted) throw DomainException("setup.already_paired", DomainException.Kind.CONFLICT)
            installation.refresh()
            repository.observeTime(clock.instant())
            identity.seedRoles()
            settings.initialise(response.clinicName ?: response.clinicCode)
            audit.record(
                AuditEvent(
                    "installation.paired", "installation", response.deviceId,
                    after = mapOf("clinicCode" to response.clinicCode, "branchCode" to response.branchCode, "vendorUrl" to url),
                    actor = SYSTEM_SETUP,
                ),
            )
        }
        response.licenseFile?.let { file ->
            runCatching { licenses.install(file.toByteArray(Charsets.UTF_8), "pairing") }
                .onFailure { log.error("License received at pairing could not be installed", it) }
        }
        return status()
    }

    fun createAdmin(fullName: String?, login: String?, password: String?, vendorSupportPassword: String?): UUID {
        if (installation.installation() == null) throw DomainException("setup.not_paired")
        return identity.createFirstAdmin(fullName.orEmpty(), login.orEmpty(), password.orEmpty(), vendorSupportPassword)
    }

    private fun validateUrl(raw: String?): String {
        val text = Text.clean(raw)?.trimEnd('/') ?: throw ValidationException("vendorUrl", "validation.not_blank")
        val uri = runCatching { URI(text) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        val valid = uri?.host != null && (scheme == "https" || (scheme == "http" && properties.allowInsecureUrl))
        if (!valid) throw ValidationException("vendorUrl", "validation.vendor_url")
        return text
    }

    private fun hostname(): String = runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("clinic-server").take(255)

    private fun appVersion(): String = build?.version ?: "dev"

    private companion object {
        val SYSTEM_SETUP = SimpleActor(ActorType.SYSTEM, null, "setup")
    }
}
