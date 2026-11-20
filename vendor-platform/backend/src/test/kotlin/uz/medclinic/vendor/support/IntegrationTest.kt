package uz.medclinic.vendor.support

import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import uz.medclinic.common.Ids
import uz.medclinic.vendor.identity.application.LoginThrottle
import uz.medclinic.vendor.identity.domain.StaffAccount
import uz.medclinic.vendor.identity.domain.StaffRepository
import uz.medclinic.vendor.identity.domain.VendorRole
import uz.medclinic.vendor.identity.security.StaffPrincipal

/**
 * Base class for API/SQL integration tests: one PostgreSQL container and one Spring context are shared by
 * all subclasses; every test starts from empty business tables.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig::class, IntegrationTest.ClockConfig::class)
abstract class IntegrationTest {
    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var jdbc: JdbcClient

    @Autowired
    protected lateinit var json: JsonMapper

    @Autowired
    protected lateinit var clock: MutableClock

    @Autowired
    protected lateinit var staffRepository: StaffRepository

    @Autowired
    protected lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var loginThrottle: LoginThrottle

    @BeforeEach
    fun resetState() {
        clock.set(DEFAULT_NOW)
        loginThrottle.clear()
        val tables = jdbc.sql(
            """
            select string_agg(format('%I.%I', schemaname, tablename), ', ')
            from pg_tables
            where schemaname = 'public' and tablename <> 'flyway_schema_history'
            """.trimIndent(),
        ).query(String::class.java).optional().orElse(null)
        if (!tables.isNullOrBlank()) jdbc.sql("truncate table $tables restart identity cascade").update()
        // Standalone sequences (e.g. human-readable codes) are not reset by "restart identity".
        jdbc.sql("select sequence_name from information_schema.sequences where sequence_schema = 'public'")
            .query(String::class.java).list()
            .forEach { jdbc.sql("alter sequence \"$it\" restart").update() }
    }

    /** Inserts a staff account with a known password (bypassing the temporary-password flow). */
    protected fun createStaff(
        login: String = "owner",
        role: VendorRole = VendorRole.OWNER,
        password: String = PASSWORD,
        mustChangePassword: Boolean = false,
        active: Boolean = true,
    ): StaffAccount {
        val now = clock.instant()
        val account = StaffAccount(
            id = Ids.newId(), login = login, fullName = "Test ${login.replaceFirstChar(Char::uppercase)}", role = role,
            passwordHash = passwordEncoder.encode(password)!!, active = active, mustChangePassword = mustChangePassword,
            passwordChangedAt = now, lastLoginAt = null, createdAt = now, updatedAt = now, version = 0,
        )
        staffRepository.insert(account)
        return account
    }

    /**
     * Authenticates a MockMvc request as [staff] (no real session) and adds a real CSRF cookie + header.
     * Spring Security's `csrf()` post-processor is deliberately not used: it replaces the token repository
     * for the whole context, which breaks the real cookie-based login tests.
     */
    protected fun asStaff(staff: StaffAccount): RequestPostProcessor {
        val csrf = csrfCookie()
        return RequestPostProcessor { request ->
            request.setCookies(*(request.cookies?.toList().orEmpty() + csrf).toTypedArray())
            request.addHeader("X-XSRF-TOKEN", csrf.value)
            authentication(StaffPrincipal.of(staff, clock.instant()).toAuthentication()).postProcessRequest(request)
        }
    }

    /** Real login through the API: returns the cookies (session + CSRF) of the new session. */
    protected fun login(login: String, password: String = PASSWORD): Cookies {
        val csrf = csrfCookie()
        val result = mockMvc.post("/api/v1/auth/login") {
            cookie(csrf)
            header("X-XSRF-TOKEN", csrf.value)
            contentType = MediaType.APPLICATION_JSON
            content = """{"login":"$login","password":"$password"}"""
        }.andExpect { status { isOk() } }.andReturn()
        val session = result.response.getCookie(SESSION_COOKIE)
            ?: error("no session cookie: " + result.response.getHeaders("Set-Cookie"))
        return Cookies(session, csrf)
    }

    protected fun csrfCookie(): Cookie {
        val result = mockMvc.get("/api/v1/auth/csrf").andReturn()
        return result.response.getCookie("XSRF-TOKEN") ?: error("no csrf cookie")
    }

    protected fun body(content: String): JsonNode = json.readTree(content)

    data class Cookies(val session: Cookie, val csrf: Cookie) {
        fun apply(): RequestPostProcessor = RequestPostProcessor { request ->
            request.setCookies(session, csrf)
            request.addHeader("X-XSRF-TOKEN", csrf.value)
            request
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class ClockConfig {
        @Bean
        @Primary
        fun mutableClock(): MutableClock = MutableClock(DEFAULT_NOW)
    }

    companion object {
        val DEFAULT_NOW: java.time.Instant = java.time.Instant.parse("2026-11-20T06:00:00Z")
        const val PASSWORD = "Correct-Horse-42"
        const val SESSION_COOKIE = "VSESSION"
    }
}
