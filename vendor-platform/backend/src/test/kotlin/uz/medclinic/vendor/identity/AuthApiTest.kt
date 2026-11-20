package uz.medclinic.vendor.identity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import uz.medclinic.vendor.identity.domain.VendorRole
import uz.medclinic.vendor.support.IntegrationTest
import java.time.Duration

class AuthApiTest : IntegrationTest() {
    @Test
    fun `login creates a session that can read me`() {
        createStaff("owner")

        val cookies = login("OWNER")

        mockMvc.get("/api/v1/auth/me") { with(cookies.apply()) }.andExpect {
            status { isOk() }
            jsonPath("$.login") { value("owner") }
            jsonPath("$.role") { value("OWNER") }
            jsonPath("$.permissions.length()") { value(10) }
        }
        val actions = jdbc.sql("select action from audit_log order by occurred_at").query(String::class.java).list()
        assertThat(actions).containsExactly("auth.login_succeeded")
    }

    @Test
    fun `wrong password, unknown login and inactive account give the same error`() {
        createStaff("owner")
        createStaff("gone", active = false)

        listOf("owner" to "wrong-password-1", "nobody" to PASSWORD, "gone" to PASSWORD).forEach { (login, password) ->
            val csrf = csrfCookie()
            mockMvc.post("/api/v1/auth/login") {
                cookie(csrf); header("X-XSRF-TOKEN", csrf.value)
                contentType = MediaType.APPLICATION_JSON
                content = """{"login":"$login","password":"$password"}"""
            }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("auth.bad_credentials") }
            }
        }
        assertThat(jdbc.sql("select count(*) from audit_log where action = 'auth.login_failed'").query(Long::class.java).single())
            .isEqualTo(3)
    }

    @Test
    fun `repeated failures are throttled with retry after`() {
        createStaff("owner")
        val csrf = csrfCookie()
        fun attempt(password: String) = mockMvc.post("/api/v1/auth/login") {
            cookie(csrf); header("X-XSRF-TOKEN", csrf.value)
            contentType = MediaType.APPLICATION_JSON
            content = """{"login":"owner","password":"$password"}"""
        }

        repeat(5) { attempt("wrong-password-x") }

        attempt(PASSWORD).andExpect {
            status { isTooManyRequests() }
            header { string("Retry-After", "900") }
            jsonPath("$.code") { value("auth.too_many_attempts") }
        }
    }

    @Test
    fun `api requires authentication and csrf`() {
        mockMvc.get("/api/v1/auth/me").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("auth.unauthenticated") }
        }
        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"login":"owner","password":"x"}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("auth.csrf_invalid") }
        }
    }

    @Test
    fun `temporary password restricts the api until it is changed`() {
        createStaff("fresh", mustChangePassword = true)
        val cookies = login("fresh")

        mockMvc.get("/api/v1/audit") { with(cookies.apply()) }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("auth.password_change_required") }
        }
        mockMvc.post("/api/v1/auth/password") {
            with(cookies.apply())
            contentType = MediaType.APPLICATION_JSON
            content = """{"currentPassword":"$PASSWORD","newPassword":"short"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.fieldErrors[0].code") { value("validation.password_policy") }
        }
        mockMvc.post("/api/v1/auth/password") {
            with(cookies.apply())
            contentType = MediaType.APPLICATION_JSON
            content = """{"currentPassword":"$PASSWORD","newPassword":"A-much-better-passphrase"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.mustChangePassword") { value(false) }
        }

        mockMvc.get("/api/v1/audit") { with(cookies.apply()) }.andExpect { status { isOk() } }
        login("fresh", "A-much-better-passphrase")
    }

    @Test
    fun `sessions expire after the absolute lifetime`() {
        createStaff("owner")
        val cookies = login("owner")

        clock.advance(Duration.ofHours(12).plusMinutes(1))

        mockMvc.get("/api/v1/auth/me") { with(cookies.apply()) }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("auth.session_expired") }
        }
    }

    @Test
    fun `logout ends the session`() {
        createStaff("owner")
        val cookies = login("owner")

        mockMvc.post("/api/v1/auth/logout") { with(cookies.apply()) }.andExpect { status { isNoContent() } }

        mockMvc.get("/api/v1/auth/me") { with(cookies.apply()) }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `deactivating a staff member ends their sessions`() {
        val owner = createStaff("owner")
        val support = createStaff("support", role = VendorRole.SUPPORT)
        val supportSession = login("support")

        mockMvc.post("/api/v1/staff/${support.id}/deactivate") {
            with(asStaff(owner))
            contentType = MediaType.APPLICATION_JSON
            content = """{"version":0}"""
        }.andExpect { status { isOk() } }

        mockMvc.get("/api/v1/auth/me") { with(supportSession.apply()) }.andExpect { status { isUnauthorized() } }
    }
}
