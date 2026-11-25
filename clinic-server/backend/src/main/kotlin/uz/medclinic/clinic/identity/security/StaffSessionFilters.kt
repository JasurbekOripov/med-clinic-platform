package uz.medclinic.clinic.identity.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import uz.medclinic.platform.web.ProblemWriter
import java.time.Clock
import java.time.Duration

private fun currentStaff(): StaffPrincipal? = SecurityContextHolder.getContext().authentication?.principal as? StaffPrincipal

/** Ends sessions older than the absolute lifetime, even if they were used continuously. */
class AbsoluteSessionTimeoutFilter(
    private val maxAge: Duration,
    private val clock: Clock,
    private val problems: ProblemWriter,
) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val principal = currentStaff()
        if (principal != null && principal.authenticatedAt.plus(maxAge).isBefore(clock.instant())) {
            request.getSession(false)?.invalidate()
            SecurityContextHolder.clearContext()
            problems.write(response, HttpStatus.UNAUTHORIZED, "auth.session_expired")
            return
        }
        chain.doFilter(request, response)
    }
}

/** While a temporary password is in use, only the endpoints needed to replace it are reachable. */
class PasswordChangeRequiredFilter(private val problems: ProblemWriter) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val principal = currentStaff()
        if (principal != null && principal.mustChangePassword && request.requestURI.startsWith("/api/") &&
            request.requestURI !in ALLOWED
        ) {
            problems.write(response, HttpStatus.FORBIDDEN, "auth.password_change_required")
            return
        }
        chain.doFilter(request, response)
    }

    private companion object {
        val ALLOWED = setOf("/api/v1/auth/me", "/api/v1/auth/password", "/api/v1/auth/logout", "/api/v1/auth/csrf", "/api/v1/license/status")
    }
}
