package uz.medclinic.clinic.identity.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.AuthorizationFilter
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextHolderFilter
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.security.web.csrf.CsrfException
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
import org.springframework.session.web.http.CookieSerializer
import org.springframework.session.web.http.DefaultCookieSerializer
import uz.medclinic.platform.web.ProblemWriter
import java.time.Clock

/**
 * Security for the clinic web app and `/api/v1`: server-side sessions (Spring Session JDBC), cookie-to-header
 * CSRF, permission checks on every endpoint via method security.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class StaffSecurityConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    /** Explicit session cookie: name, HttpOnly, Secure, SameSite=Lax. */
    @Bean
    fun sessionCookieSerializer(properties: IdentityProperties): CookieSerializer = DefaultCookieSerializer().apply {
        setCookieName(SESSION_COOKIE)
        setCookiePath("/")
        setUseHttpOnlyCookie(true)
        setUseSecureCookie(properties.sessionCookieSecure)
        setSameSite("Lax")
    }

    @Bean
    fun securityContextRepository(): SecurityContextRepository = HttpSessionSecurityContextRepository()

    @Bean
    @Order(STAFF_CHAIN_ORDER)
    fun staffSecurityFilterChain(
        http: HttpSecurity,
        contextRepository: SecurityContextRepository,
        properties: IdentityProperties,
        problems: ProblemWriter,
        clock: Clock,
    ): SecurityFilterChain {
        http
            .csrf { it.spa() }
            .securityContext { it.securityContextRepository(contextRepository) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            .requestCache { it.disable() }
            .authorizeHttpRequests {
                it.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                    // First-run setup and the license status page work before anyone can log in;
                    // the setup service itself refuses once setup is complete.
                    .requestMatchers("/api/v1/setup/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/license/status", "/api/v1/settings/public", "/api/v1/settings/logo").permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .requestMatchers("/api/**", "/actuator/**").authenticated()
                    // Everything else is the single-page console (static assets + client-side routes).
                    .anyRequest().permitAll()
            }
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ ->
                    problems.write(response, HttpStatus.UNAUTHORIZED, "auth.unauthenticated")
                }
                it.accessDeniedHandler { _, response, ex ->
                    problems.write(response, HttpStatus.FORBIDDEN, if (ex is CsrfException) "auth.csrf_invalid" else "auth.forbidden")
                }
            }
            .headers { headers ->
                headers.frameOptions { it.deny() }
                headers.contentSecurityPolicy {
                    it.policyDirectives(
                        "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; " +
                            "object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'",
                    )
                }
                headers.referrerPolicy { it.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN) }
            }
            .addFilterAfter(
                AbsoluteSessionTimeoutFilter(properties.sessionAbsoluteTimeout, clock, problems),
                SecurityContextHolderFilter::class.java,
            )
            .addFilterBefore(PasswordChangeRequiredFilter(problems), AuthorizationFilter::class.java)
        return http.build()
    }

    companion object {
        const val STAFF_CHAIN_ORDER = 100
        const val SESSION_COOKIE = "CSESSION"
    }
}
