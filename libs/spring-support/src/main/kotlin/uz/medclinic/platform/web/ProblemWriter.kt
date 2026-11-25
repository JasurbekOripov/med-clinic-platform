package uz.medclinic.platform.web

import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/** Writes problem+json from servlet filters / security handlers, which run outside Spring MVC. */
@Component
class ProblemWriter(private val mapper: JsonMapper) {
    fun write(response: HttpServletResponse, status: HttpStatus, code: String, params: Map<String, Any?> = emptyMap()) {
        if (response.isCommitted) return
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        mapper.writeValue(response.outputStream, ProblemDetails.of(status, code, params))
    }
}
