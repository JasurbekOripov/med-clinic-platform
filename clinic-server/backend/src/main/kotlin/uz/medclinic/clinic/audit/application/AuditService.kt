package uz.medclinic.clinic.audit.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import uz.medclinic.common.Ids
import uz.medclinic.clinic.audit.api.AuditEvent
import uz.medclinic.clinic.audit.api.AuditRecorder
import uz.medclinic.clinic.audit.infrastructure.AuditRepository
import uz.medclinic.clinic.shared.ClinicContext
import uz.medclinic.platform.domain.Page
import uz.medclinic.platform.domain.PageQuery
import uz.medclinic.platform.security.CurrentActor
import uz.medclinic.platform.web.ClientInfo
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class AuditEntry(
    val id: UUID,
    val occurredAt: Instant,
    val actorType: String,
    val actorId: UUID?,
    val actorName: String,
    val action: String,
    val entityType: String,
    val entityId: UUID?,
    val clinicId: UUID?,
    val branchId: UUID?,
    val before: String?,
    val after: String?,
    val ip: String?,
    val userAgent: String?,
    val requestId: String?,
)

data class AuditFilter(
    val entityType: String? = null,
    val entityId: UUID? = null,
    val actorId: UUID? = null,
    val action: String? = null,
    val from: Instant? = null,
    val to: Instant? = null,
)

@Service
class AuditService(
    private val repository: AuditRepository,
    private val clinic: ClinicContext,
    mapper: JsonMapper,
    private val clock: Clock,
) : AuditRecorder {
    private val json = AuditJson(mapper)

    @Transactional(propagation = Propagation.MANDATORY)
    override fun record(event: AuditEvent) {
        val actor = event.actor ?: CurrentActor.get()
        val client = ClientInfo.current()
        val identity = clinic.currentOrNull()
        repository.insert(
            AuditEntry(
                id = Ids.newId(),
                occurredAt = clock.instant(),
                actorType = actor.actorType.name,
                actorId = actor.actorId,
                actorName = actor.actorName.take(MAX_NAME),
                action = event.action,
                entityType = event.entityType,
                entityId = event.entityId,
                clinicId = identity?.clinicId,
                branchId = identity?.branchId,
                before = json.toJson(event.before),
                after = json.toJson(event.after),
                ip = client.ip,
                userAgent = client.userAgent,
                requestId = client.requestId,
            ),
        )
    }

    @Transactional(readOnly = true)
    fun search(filter: AuditFilter, page: PageQuery): Page<AuditEntry> = repository.search(filter, page)

    private companion object {
        const val MAX_NAME = 200
    }
}
