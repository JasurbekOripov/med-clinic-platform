package uz.medclinic.clinic.patients.application

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import uz.medclinic.clinic.audit.api.AuditEvent
import uz.medclinic.clinic.audit.api.AuditRecorder
import uz.medclinic.clinic.shared.CurrentPermissions
import uz.medclinic.clinic.shared.Permission
import uz.medclinic.platform.security.CurrentActor
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Owner's decision: the Admin can see which doctor, lab or nurse opened which patient card. The page reloads and
 * refocuses often, so one person opening the same card again within [WINDOW] is recorded once; the window is kept in
 * memory (a restart may record one extra row, which is harmless).
 */
@Component
class PatientViewLog(private val audit: AuditRecorder, private val clock: Clock) {
    private val lastSeen = ConcurrentHashMap<Pair<UUID, UUID>, Instant>()

    @Transactional
    fun opened(patientId: UUID) {
        if (!CurrentPermissions.hasAny(*MEDICAL)) return
        val actor = CurrentActor.get().actorId ?: return
        val now = clock.instant()
        val key = actor to patientId
        val previous = lastSeen[key]
        if (previous != null && Duration.between(previous, now) < WINDOW) return
        lastSeen[key] = now
        if (lastSeen.size > MAX_ENTRIES) lastSeen.entries.removeIf { Duration.between(it.value, now) >= WINDOW }
        audit.record(AuditEvent("patient.viewed", "patient", patientId))
    }

    private companion object {
        val WINDOW: Duration = Duration.ofMinutes(30)
        const val MAX_ENTRIES = 10_000
        val MEDICAL = arrayOf(Permission.CONSULTATION_WRITE, Permission.LAB_WORK, Permission.INPATIENT_WORK, Permission.MEDICAL_HISTORY_VIEW)
    }
}
