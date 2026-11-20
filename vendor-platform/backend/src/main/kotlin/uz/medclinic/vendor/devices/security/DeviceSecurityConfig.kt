package uz.medclinic.vendor.devices.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.AuthorizationFilter
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.security.web.context.NullSecurityContextRepository
import org.springframework.web.filter.OncePerRequestFilter
import uz.medclinic.vendor.devices.api.AuthenticatedDevice
import uz.medclinic.vendor.devices.application.DeviceProperties
import uz.medclinic.vendor.devices.domain.DeviceRepository
import uz.medclinic.vendor.devices.domain.DeviceSecrets
import uz.medclinic.vendor.shared.web.ClientInfo
import uz.medclinic.vendor.shared.web.ProblemWriter
import uz.medclinic.vendorprotocol.DeviceApi
import java.time.Clock

/**
 * Stateless bearer-token security for the Clinic Server API. Evaluated before the staff chain; no session,
 * no CSRF (no cookies are used).
 */
@Configuration(proxyBeanMethods = false)
class DeviceSecurityConfig {
    @Bean
    @Order(DEVICE_CHAIN_ORDER)
    fun deviceSecurityFilterChain(
        http: HttpSecurity,
        devices: DeviceRepository,
        properties: DeviceProperties,
        problems: ProblemWriter,
        clock: Clock,
    ): SecurityFilterChain {
        http
            .securityMatcher("${DeviceApi.BASE_PATH}/**")
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .securityContext { it.securityContextRepository(NullSecurityContextRepository()) }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            .requestCache { it.disable() }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.POST, DeviceApi.PAIRING_CLAIM_PATH).permitAll()
                    .anyRequest().hasAuthority(DEVICE_AUTHORITY)
            }
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ -> problems.write(response, HttpStatus.UNAUTHORIZED, "device.unauthorized") }
                it.accessDeniedHandler { _, response, _ -> problems.write(response, HttpStatus.FORBIDDEN, "auth.forbidden") }
            }
            .addFilterBefore(DeviceTokenFilter(devices, properties, problems, clock), AuthorizationFilter::class.java)
        return http.build()
    }

    companion object {
        const val DEVICE_CHAIN_ORDER = 10
        const val DEVICE_AUTHORITY = "DEVICE"
    }
}

/** Resolves `Authorization: Bearer dvt1_…` to an active device. Not a bean, so it is not registered globally. */
class DeviceTokenFilter(
    private val devices: DeviceRepository,
    private val properties: DeviceProperties,
    private val problems: ProblemWriter,
    private val clock: Clock,
) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header == null) {
            chain.doFilter(request, response)
            return
        }
        val token = header.removePrefix("${DeviceApi.AUTHORIZATION_SCHEME} ").trim()
        val device = token.takeIf { it.startsWith(DeviceSecrets.TOKEN_PREFIX) && it.length <= MAX_TOKEN_LENGTH }
            ?.let { devices.findActiveByTokenHash(DeviceSecrets.sha256(it)) }
        if (device == null) {
            problems.write(response, HttpStatus.UNAUTHORIZED, "device.unauthorized")
            return
        }
        val now = clock.instant()
        if (device.lastSeenAt == null || device.lastSeenAt.plus(properties.lastSeenWriteInterval).isBefore(now)) {
            devices.touch(device.id, now, ClientInfo.of(request).ip)
        }
        val principal = AuthenticatedDevice(device.id, device.clinicId, device.branchId, device.hostname)
        val authentication = PreAuthenticatedAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority(DeviceSecurityConfig.DEVICE_AUTHORITY)))
        SecurityContextHolder.getContext().authentication = authentication
        try {
            chain.doFilter(request, response)
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    private companion object {
        const val MAX_TOKEN_LENGTH = 100
    }
}
