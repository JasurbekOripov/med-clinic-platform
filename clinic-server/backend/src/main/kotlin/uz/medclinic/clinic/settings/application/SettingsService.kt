package uz.medclinic.clinic.settings.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.clinic.audit.api.AuditEvent
import uz.medclinic.clinic.audit.api.AuditRecorder
import uz.medclinic.clinic.settings.api.SettingsSetup
import uz.medclinic.clinic.settings.domain.ClinicSettings
import uz.medclinic.clinic.settings.domain.LogoImage
import uz.medclinic.clinic.settings.infrastructure.JdbcSettingsRepository
import uz.medclinic.clinic.shared.ClinicContext
import uz.medclinic.platform.domain.NotFoundException
import java.time.Clock

@Service
class SettingsService(
    private val repository: JdbcSettingsRepository,
    private val clinic: ClinicContext,
    private val audit: AuditRecorder,
    private val clock: Clock,
) : SettingsSetup {
    @Transactional
    override fun initialise(clinicName: String) {
        if (repository.insertIfMissing(clinic.current(), clinicName.take(200), clock.instant())) {
            audit.record(AuditEvent("settings.initialised", ENTITY, null, after = mapOf("name" to clinicName)))
        }
    }

    /** Null before the server is set up (login page then shows the product name). */
    @Transactional(readOnly = true)
    fun findOrNull(): ClinicSettings? = repository.find()

    @Transactional(readOnly = true)
    fun get(): ClinicSettings = repository.find() ?: throw NotFoundException(ENTITY, "clinic")

    @Transactional
    fun update(edit: ClinicSettings.Edit, version: Int): ClinicSettings {
        val before = get()
        repository.update(edit, version, clock.instant())
        val after = get()
        audit.record(AuditEvent("settings.updated", ENTITY, null, before = snapshot(before), after = snapshot(after)))
        return after
    }

    @Transactional(readOnly = true)
    fun logo(): Pair<ByteArray, String> = repository.logo() ?: throw NotFoundException("logo", "clinic")

    @Transactional
    fun setLogo(bytes: ByteArray) {
        get()
        val type = LogoImage.contentType(bytes)
        repository.setLogo(bytes, type, clock.instant())
        audit.record(AuditEvent("settings.logo_changed", ENTITY, null, after = mapOf("contentType" to type, "bytes" to bytes.size)))
    }

    @Transactional
    fun removeLogo() {
        get()
        repository.setLogo(null, null, clock.instant())
        audit.record(AuditEvent("settings.logo_removed", ENTITY, null))
    }

    private fun snapshot(s: ClinicSettings) = mapOf(
        "name" to s.name, "legalName" to s.legalName, "address" to s.address, "phone" to s.phone?.e164,
        "letterhead" to s.letterhead, "receiptHeader" to s.receiptHeader, "receiptFooter" to s.receiptFooter,
        "workingHours" to s.workingHours, "idleLockMinutes" to s.idleLockMinutes,
    )

    private companion object {
        const val ENTITY = "settings"
    }
}
