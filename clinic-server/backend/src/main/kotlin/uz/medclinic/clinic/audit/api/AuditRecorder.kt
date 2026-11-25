package uz.medclinic.clinic.audit.api

import uz.medclinic.platform.security.Actor
import java.util.UUID

/**
 * Records a sensitive action. Must be called inside the transaction that performs the change, so the
 * change and its audit row commit or roll back together. Clinic and branch ids are added automatically.
 *
 * [before]/[after] are snapshots (DTOs or maps) serialised as JSON. Never pass secrets or images; keys that look
 * like secrets are stripped defensively anyway.
 */
interface AuditRecorder {
    fun record(event: AuditEvent)
}

data class AuditEvent(
    val action: String,
    val entityType: String,
    val entityId: UUID?,
    val before: Any? = null,
    val after: Any? = null,
    /** Overrides the actor taken from the security context (e.g. the account that is just being created). */
    val actor: Actor? = null,
)
