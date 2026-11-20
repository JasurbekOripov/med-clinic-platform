package uz.medclinic.vendor.identity.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uz.medclinic.vendor.identity.application.AuthService
import uz.medclinic.vendor.identity.application.LoginResult
import uz.medclinic.vendor.identity.application.LoginThrottle
import uz.medclinic.vendor.identity.security.StaffPrincipal
import uz.medclinic.vendor.shared.domain.DomainException
import uz.medclinic.vendor.shared.web.ClientInfo
import java.util.UUID

data class LoginRequest(
    @field:NotBlank @field:Size(max = 100) val login: String,
    @field:NotBlank @field:Size(max = 200) val password: String,
)

data class ChangePasswordRequest(
    @field:NotBlank @field:Size(max = 200) val currentPassword: String,
    @field:NotBlank @field:Size(max = 200) val newPassword: String,
)

data class MeResponse(
    val id: UUID,
    val login: String,
    val fullName: String,
    val role: String,
    val permissions: List<String>,
    val mustChangePassword: Boolean,
) {
    constructor(p: StaffPrincipal) : this(p.id, p.login, p.fullName, p.role.name, p.permissions.map { it.name }.sorted(), p.mustChangePassword)
}

data class CsrfResponse(val headerName: String, val token: String)

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val auth: AuthService,
    private val throttle: LoginThrottle,
    private val contextRepository: SecurityContextRepository,
) {
    /** Touching the deferred token makes Spring Security write the `XSRF-TOKEN` cookie. */
    @GetMapping("/csrf")
    fun csrf(token: CsrfToken) = CsrfResponse(token.headerName, token.token)

    @PostMapping("/login")
    fun login(@Valid @RequestBody body: LoginRequest, request: HttpServletRequest, response: HttpServletResponse): MeResponse {
        val ip = ClientInfo.of(request).ip
        throttle.checkAllowed(body.login, ip)
        return when (val result = auth.authenticate(body.login, body.password)) {
            LoginResult.Failure -> {
                throttle.recordFailure(body.login, ip)
                throw DomainException("auth.bad_credentials", DomainException.Kind.UNAUTHORIZED)
            }
            is LoginResult.Success -> {
                throttle.recordSuccess(body.login)
                startSession(result.principal, request, response)
                MeResponse(result.principal)
            }
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@AuthenticationPrincipal principal: StaffPrincipal?, request: HttpServletRequest) {
        principal?.let(auth::recordLogout)
        request.getSession(false)?.invalidate()
        SecurityContextHolder.clearContext()
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: StaffPrincipal) = MeResponse(principal)

    @PostMapping("/password")
    fun changePassword(
        @AuthenticationPrincipal principal: StaffPrincipal,
        @Valid @RequestBody body: ChangePasswordRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): MeResponse {
        val sessionId = request.getSession(false)?.id
        val refreshed = auth.changeOwnPassword(principal, body.currentPassword, body.newPassword, sessionId)
        saveContext(refreshed, request, response)
        return MeResponse(refreshed)
    }

    /** A brand-new session id on login prevents session fixation. */
    private fun startSession(principal: StaffPrincipal, request: HttpServletRequest, response: HttpServletResponse) {
        request.getSession(false)?.invalidate()
        request.getSession(true)
        saveContext(principal, request, response)
    }

    private fun saveContext(principal: StaffPrincipal, request: HttpServletRequest, response: HttpServletResponse) {
        val context = SecurityContextHolder.createEmptyContext().apply { authentication = principal.toAuthentication() }
        SecurityContextHolder.setContext(context)
        contextRepository.saveContext(context, request, response)
    }
}
