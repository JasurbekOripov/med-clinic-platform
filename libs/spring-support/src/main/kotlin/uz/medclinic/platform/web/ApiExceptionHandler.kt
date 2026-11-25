package uz.medclinic.platform.web

import jakarta.validation.ConstraintViolation
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.domain.DomainException.Kind
import uz.medclinic.platform.domain.FieldViolation
import uz.medclinic.platform.domain.ValidationException

/**
 * Every error leaves the API as RFC 9457 problem+json with a stable `code`, optional `params` and
 * `fieldErrors`. The UI translates codes; messages here are for developers only. Highest precedence so it wins
 * over Spring Boot's generic problem-details handler.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ApiExceptionHandler : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ValidationException::class)
    fun handleValidation(ex: ValidationException): ResponseEntity<Any> =
        problem(HttpStatus.BAD_REQUEST, ex.code, ex.params, ex.violations)

    @ExceptionHandler(DomainException::class)
    fun handleDomain(ex: DomainException): ResponseEntity<Any> {
        val response = problem(statusOf(ex.kind), ex.code, ex.params)
        val retryAfter = ex.params["retryAfterSeconds"]
        return if (ex.kind == Kind.TOO_MANY_REQUESTS && retryAfter != null) {
            ResponseEntity.status(response.statusCode).header(HttpHeaders.RETRY_AFTER, retryAfter.toString()).body(response.body)
        } else {
            response
        }
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<Any> = problem(HttpStatus.FORBIDDEN, "auth.forbidden")

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<Any> =
        problem(HttpStatus.BAD_REQUEST, "request.invalid_parameter", mapOf("name" to ex.name))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<Any> {
        log.debug("Rejected request: {}", ex.message)
        return problem(HttpStatus.BAD_REQUEST, "request.invalid")
    }

    @ExceptionHandler(DuplicateKeyException::class)
    fun handleDuplicate(ex: DuplicateKeyException): ResponseEntity<Any> {
        log.warn("Unmapped unique constraint violation", ex)
        return problem(HttpStatus.CONFLICT, "common.duplicate")
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<Any> {
        log.error("Unexpected error", ex)
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "common.internal_error")
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val violations = ex.bindingResult.fieldErrors.map(::toViolation) +
            ex.bindingResult.globalErrors.map { FieldViolation(it.objectName, "validation.${snake(it.code)}") }
        return problem(HttpStatus.BAD_REQUEST, "validation.failed", violations = violations)
    }

    override fun handleHandlerMethodValidationException(
        ex: HandlerMethodValidationException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val violations = ex.parameterValidationResults.flatMap { result ->
            result.resolvableErrors.map { error ->
                FieldViolation(result.methodParameter.parameterName ?: "param", "validation.${snake(error.codes?.lastOrNull())}")
            }
        }
        return problem(HttpStatus.BAD_REQUEST, "validation.failed", violations = violations)
    }

    /** Standard Spring MVC errors (404, 405, 415, malformed JSON…) get a generic code derived from the status. */
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val code = when (statusCode.value()) {
            400 -> "request.malformed"
            404 -> "common.not_found"
            405 -> "request.method_not_allowed"
            406, 415 -> "request.unsupported_media_type"
            else -> "http.${statusCode.value()}"
        }
        val response = problem(HttpStatus.valueOf(statusCode.value()), code)
        return ResponseEntity.status(statusCode).headers(headers).body(response.body)
    }

    private fun toViolation(error: FieldError): FieldViolation {
        val attributes = runCatching { error.unwrap(ConstraintViolation::class.java).constraintDescriptor.attributes }
            .getOrNull().orEmpty()
            .filterKeys { it in EXPOSED_ATTRIBUTES }
        return FieldViolation(error.field, "validation.${snake(error.code)}", attributes)
    }

    private fun statusOf(kind: Kind): HttpStatus = when (kind) {
        Kind.VALIDATION -> HttpStatus.BAD_REQUEST
        Kind.NOT_FOUND -> HttpStatus.NOT_FOUND
        Kind.CONFLICT -> HttpStatus.CONFLICT
        Kind.RULE_VIOLATION -> HttpStatus.UNPROCESSABLE_CONTENT
        Kind.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED
        Kind.FORBIDDEN -> HttpStatus.FORBIDDEN
        Kind.TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS
    }

    private fun problem(
        status: HttpStatus,
        code: String,
        params: Map<String, Any?> = emptyMap(),
        violations: List<FieldViolation> = emptyList(),
    ): ResponseEntity<Any> = ResponseEntity.status(status).body(ProblemDetails.of(status, code, params, violations))

    private companion object {
        val EXPOSED_ATTRIBUTES = setOf("min", "max", "value", "inclusive")
        val CAMEL = Regex("([a-z0-9])([A-Z])")

        fun snake(code: String?): String = code?.replace(CAMEL, "$1_$2")?.lowercase() ?: "invalid"
    }
}

object ProblemDetails {
    fun of(
        status: HttpStatus,
        code: String,
        params: Map<String, Any?> = emptyMap(),
        violations: List<FieldViolation> = emptyList(),
    ): ProblemDetail = ProblemDetail.forStatus(status).apply {
        title = status.reasonPhrase
        setProperty("code", code)
        if (params.isNotEmpty()) setProperty("params", params)
        if (violations.isNotEmpty()) setProperty("fieldErrors", violations)
        ClientInfo.current().requestId?.let { setProperty("requestId", it) }
    }
}
