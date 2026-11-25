package uz.medclinic.platform.domain

/**
 * Business failure with a stable, translatable [code]. The web layer maps [kind] to an HTTP status;
 * the domain never knows about HTTP.
 */
open class DomainException(
    val code: String,
    val kind: Kind = Kind.RULE_VIOLATION,
    val params: Map<String, Any?> = emptyMap(),
    cause: Throwable? = null,
) : RuntimeException(code, cause) {
    enum class Kind { VALIDATION, NOT_FOUND, CONFLICT, RULE_VIOLATION, UNAUTHORIZED, FORBIDDEN, TOO_MANY_REQUESTS }
}

class NotFoundException(entity: String, id: Any) :
    DomainException("common.not_found", Kind.NOT_FOUND, mapOf("entity" to entity, "id" to id.toString()))

/** Optimistic locking failure: someone else changed the row since the client loaded it. */
class VersionConflictException(entity: String, id: Any) :
    DomainException("common.version_conflict", Kind.CONFLICT, mapOf("entity" to entity, "id" to id.toString()))

data class FieldViolation(val field: String, val code: String, val params: Map<String, Any?> = emptyMap())

class ValidationException(val violations: List<FieldViolation>) :
    DomainException("validation.failed", Kind.VALIDATION) {
    constructor(field: String, code: String, params: Map<String, Any?> = emptyMap()) :
        this(listOf(FieldViolation(field, code, params)))
}
