package uz.medclinic.vendor.audit.web

import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import uz.medclinic.vendor.audit.application.AuditEntry
import uz.medclinic.vendor.audit.application.AuditFilter
import uz.medclinic.vendor.audit.application.AuditService
import uz.medclinic.platform.domain.PageQuery
import uz.medclinic.vendor.shared.security.Permissions
import uz.medclinic.platform.web.PageResponse
import java.time.Instant
import java.util.UUID

data class AuditEntryResponse(
    val id: UUID,
    val occurredAt: Instant,
    val actorType: String,
    val actorId: UUID?,
    val actorName: String,
    val action: String,
    val entityType: String,
    val entityId: UUID?,
    val clinicId: UUID?,
    val before: JsonNode?,
    val after: JsonNode?,
    val ip: String?,
    val requestId: String?,
)

@RestController
@RequestMapping("/api/v1/audit")
class AuditController(private val audit: AuditService, private val mapper: JsonMapper) {
    @GetMapping
    @PreAuthorize(Permissions.HAS_AUDIT_VIEW)
    fun search(
        @RequestParam(required = false) entityType: String?,
        @RequestParam(required = false) entityId: UUID?,
        @RequestParam(required = false) actorId: UUID?,
        @RequestParam(required = false) clinicId: UUID?,
        @RequestParam(required = false) action: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ): PageResponse<AuditEntryResponse> {
        val filter = AuditFilter(entityType, entityId, actorId, clinicId, action, from, to)
        return PageResponse.of(audit.search(filter, PageQuery.of(page, size)), ::toResponse)
    }

    private fun toResponse(entry: AuditEntry) = AuditEntryResponse(
        id = entry.id,
        occurredAt = entry.occurredAt,
        actorType = entry.actorType,
        actorId = entry.actorId,
        actorName = entry.actorName,
        action = entry.action,
        entityType = entry.entityType,
        entityId = entry.entityId,
        clinicId = entry.clinicId,
        before = entry.before?.let(mapper::readTree),
        after = entry.after?.let(mapper::readTree),
        ip = entry.ip,
        requestId = entry.requestId,
    )
}
