package uz.medclinic.clinic.support

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
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import uz.medclinic.clinic.identity.api.IdentitySetup
import uz.medclinic.clinic.identity.application.Principals
import uz.medclinic.clinic.identity.domain.RoleRepository
import uz.medclinic.clinic.identity.domain.RoleTemplate
import uz.medclinic.clinic.identity.domain.StaffMember
import uz.medclinic.clinic.identity.domain.StaffProfile
import uz.medclinic.clinic.identity.domain.StaffRepository
import uz.medclinic.clinic.settings.api.SettingsSetup
import uz.medclinic.clinic.vendorlink.application.InstallationService
import uz.medclinic.clinic.vendorlink.application.LicenseService
import uz.medclinic.clinic.vendorlink.infrastructure.Installation
import uz.medclinic.clinic.vendorlink.infrastructure.JdbcInstallationRepository
import uz.medclinic.clinic.vendorlink.infrastructure.TokenCipher
import uz.medclinic.common.Ids
import uz.medclinic.license.LicensePayload
import uz.medclinic.platform.security.LoginThrottle
import java.time.Instant
import java.util.UUID

/**
 * One PostgreSQL container and one Spring context for all tests; each test starts from an empty database.
 * [pairClinic] puts the server in the state right after first-run setup (paired, roles seeded, licensed).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig::class, IntegrationTest.ClockConfig::class)
abstract class IntegrationTest {
    @Autowired protected lateinit var mockMvc: MockMvc
    @Autowired protected lateinit var jdbc: JdbcClient
    @Autowired protected lateinit var json: JsonMapper
    @Autowired protected lateinit var clock: MutableClock
    @Autowired protected lateinit var staffRepository: StaffRepository
    @Autowired protected lateinit var roleRepository: RoleRepository
    @Autowired protected lateinit var passwordEncoder: PasswordEncoder
    @Autowired protected lateinit var installation: InstallationService
    @Autowired protected lateinit var licenses: LicenseService
    @Autowired protected lateinit var installationRepository: JdbcInstallationRepository
    @Autowired protected lateinit var cipher: TokenCipher
    @Autowired protected lateinit var identitySetup: IdentitySetup
    @Autowired protected lateinit var settingsSetup: SettingsSetup
    @Autowired protected lateinit var principals: Principals
    @Autowired protected lateinit var transactions: TransactionTemplate
    @Autowired private lateinit var loginThrottle: LoginThrottle

    protected val clinicId: UUID = UUID.fromString("0193a000-0000-7000-8000-0000000000c1")
    protected val branchId: UUID = UUID.fromString("0193a000-0000-7000-8000-0000000000b1")

    @BeforeEach
    fun resetState() {
        clock.set(DEFAULT_NOW)
        loginThrottle.clear()
        val tables = jdbc.sql(
            "select string_agg(format('%I.%I', schemaname, tablename), ', ') from pg_tables " +
                "where schemaname = 'public' and tablename <> 'flyway_schema_history'",
        ).query(String::class.java).optional().orElse(null)
        if (!tables.isNullOrBlank()) jdbc.sql("truncate table $tables restart identity cascade").update()
        jdbc.sql("insert into vendor_link_state (id) values (1)").update()
        installation.refresh()
        licenses.reload()
    }

    /** Paired with the vendor, roles and settings created, license installed (unless [license] is null). */
    protected fun pairClinic(
        license: LicensePayload? = TestLicenses.payload(clinicId),
        vendorUrl: String = "http://vendor.test",
    ) {
        transactions.executeWithoutResult {
            installationRepository.insert(
                Installation(clinicId, branchId, "CL-0001", "CL-0001-01", vendorUrl, Ids.newId(), cipher.encrypt("dvt1_test"), clock.instant()),
            )
            installation.refresh()
            identitySetup.seedRoles()
            settingsSetup.initialise("Shifo Med")
        }
        license?.let { licenses.install(TestLicenses.file(it), "test") }
    }

    protected fun createStaff(
        login: String = "admin",
        template: RoleTemplate = RoleTemplate.ADMIN,
        password: String = PASSWORD,
        mustChangePassword: Boolean = false,
        active: Boolean = true,
        roleId: UUID? = null,
    ): StaffMember {
        val now = clock.instant()
        val role = roleId ?: roleRepository.findByTemplate(template)!!.id
        val member = StaffMember(
            id = Ids.newId(), roleId = role, login = login,
            profile = StaffProfile("Test ${login.replaceFirstChar(Char::uppercase)}", null, null),
            passwordHash = passwordEncoder.encode(password)!!, active = active, mustChangePassword = mustChangePassword,
            passwordChangedAt = now, lastLoginAt = null, granted = emptySet(), denied = emptySet(), hasSignature = false,
            createdAt = now, updatedAt = now, version = 0,
        )
        staffRepository.insert(member)
        return member
    }

    /** Authenticates a request as [staff] (no real session) with a real CSRF cookie + header. */
    protected fun asStaff(staff: StaffMember): RequestPostProcessor {
        val csrf = csrfCookie()
        val principal = principals.of(staffRepository.findById(staff.id)!!, clock.instant())
        return RequestPostProcessor { request ->
            request.setCookies(*(request.cookies?.toList().orEmpty() + csrf).toTypedArray())
            request.addHeader("X-XSRF-TOKEN", csrf.value)
            authentication(principal.toAuthentication()).postProcessRequest(request)
        }
    }

    protected fun login(login: String, password: String = PASSWORD): Cookies {
        val csrf = csrfCookie()
        val result = mockMvc.post("/api/v1/auth/login") {
            cookie(csrf); header("X-XSRF-TOKEN", csrf.value)
            contentType = MediaType.APPLICATION_JSON
            content = """{"login":"$login","password":"$password"}"""
        }.andExpect { status { isOk() } }.andReturn()
        return Cookies(result.response.getCookie("CSESSION") ?: error("no session cookie"), csrf)
    }

    protected fun csrfCookie(): Cookie =
        mockMvc.get("/api/v1/auth/csrf").andReturn().response.getCookie("XSRF-TOKEN") ?: error("no csrf cookie")

    protected fun anonymousCsrf(): RequestPostProcessor {
        val csrf = csrfCookie()
        return RequestPostProcessor { request ->
            request.setCookies(csrf)
            request.addHeader("X-XSRF-TOKEN", csrf.value)
            request
        }
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
        val DEFAULT_NOW: Instant = Instant.parse("2026-11-25T06:00:00Z")
        const val PASSWORD = "Correct-Horse-42"
    }
}
