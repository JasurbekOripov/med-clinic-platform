package uz.medclinic.clinic.vendorlink.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import uz.medclinic.clinic.vendorlink.application.LicenseService
import uz.medclinic.platform.web.ProblemWriter

/**
 * Stops all work when the license is not usable (expired after grace, revoked, missing, clock moved back).
 * Signing in, the license page, first-run setup and the vendor connection stay available, so the Admin can see
 * what is wrong and the license can arrive or be uploaded. No data is deleted or hidden permanently.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class LicenseEnforcementFilter(private val licenses: LicenseService, private val problems: ProblemWriter) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return !path.startsWith("/api/") || ALWAYS_ALLOWED.any { path == it || path.startsWith("$it/") }
    }

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val status = licenses.status()
        if (!status.usable) {
            problems.write(response, HttpStatus.FORBIDDEN, "license.${status.state.name.lowercase()}")
            return
        }
        chain.doFilter(request, response)
    }

    private companion object {
        val ALWAYS_ALLOWED = listOf(
            "/api/v1/auth",
            "/api/v1/setup",
            "/api/v1/license",
            "/api/v1/settings/public",
            "/api/v1/settings/logo",
        )
    }
}
