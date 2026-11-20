package uz.medclinic.vendor.monitoring

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import uz.medclinic.vendor.identity.domain.VendorRole
import uz.medclinic.vendor.monitoring.application.AlertEvaluator
import uz.medclinic.vendor.support.TenantIntegrationTest
import java.time.Duration
import java.util.UUID

class MonitoringApiTest : TenantIntegrationTest() {
    @Autowired
    private lateinit var evaluator: AlertEvaluator

    private fun openAlertTypes(): List<String> =
        jdbc.sql("select type from monitoring_alert where resolved_at is null order by type").query { rs, _ -> rs.getString(1) }.list()

    @Test
    fun `heartbeats are idempotent and update the latest health`() {
        val owner = createStaff("owner")
        val device = pair(owner, newBranch(owner, newClinic(owner)))
        val id = UUID.randomUUID()

        heartbeat(device.token, id).andExpect {
            status { isOk() }
            jsonPath("$.serverTime") { value("2026-11-20T06:00:00Z") }
            jsonPath("$.nextHeartbeatInSeconds") { value(60) }
            jsonPath("$.license.installedLicenseStatus") { value("NONE") }
        }
        clock.advance(Duration.ofSeconds(30))
        heartbeat(device.token, id).andExpect { status { isOk() } }

        assertThat(jdbc.sql("select count(*) from heartbeat").query(Long::class.java).single()).isEqualTo(1)
        val lastHeartbeat = jdbc.sql("select last_heartbeat_at::text from device_health").query(String::class.java).single()
        assertThat(lastHeartbeat).startsWith("2026-11-20 06:00:00")
    }

    @Test
    fun `heartbeat reports license sync state so renewals are picked up`() {
        val owner = createStaff("owner")
        val clinicId = newClinic(owner)
        val device = pair(owner, newBranch(owner, clinicId))
        val issued = body(
            mockMvc.post("/api/v1/clinics/$clinicId/licenses") {
                with(asStaff(owner)); contentType = MediaType.APPLICATION_JSON
                content = """{"plan":"STANDARD","modules":["LAB"],"maxUsers":5,"maxBranches":1,"startsOn":"2026-11-20","endsOn":"2027-11-19"}"""
            }.andReturn().response.contentAsString,
        ).path("id").asString()

        heartbeat(device.token, installedLicenseId = UUID.fromString(issued)).andExpect {
            jsonPath("$.license.installedLicenseStatus") { value("CURRENT") }
            jsonPath("$.license.currentLicenseId") { value(issued) }
        }
        val renewed = body(
            mockMvc.post("/api/v1/licenses/$issued/renew") {
                with(asStaff(owner)); contentType = MediaType.APPLICATION_JSON
                content = """{"plan":"STANDARD","modules":["LAB"],"maxUsers":9,"maxBranches":1,"startsOn":"2026-11-20","endsOn":"2028-11-19","version":0}"""
            }.andReturn().response.contentAsString,
        ).path("id").asString()

        heartbeat(device.token, installedLicenseId = UUID.fromString(issued)).andExpect {
            jsonPath("$.license.installedLicenseStatus") { value("SUPERSEDED") }
            jsonPath("$.license.currentLicenseId") { value(renewed) }
        }
        heartbeat(device.token, installedLicenseId = UUID.randomUUID()).andExpect {
            jsonPath("$.license.installedLicenseStatus") { value("UNKNOWN") }
        }
    }

    @Test
    fun `health alerts open on a bad heartbeat and resolve on a good one`() {
        val owner = createStaff("owner")
        val device = pair(owner, newBranch(owner, newClinic(owner)))

        heartbeat(device.token, freeBytes = 1_000_000_000, sentAt = clock.instant().minusSeconds(900).toString(),
            backup = """{"completedAt":"2026-11-20T02:00:00Z","status":"FAILED","lastSuccessAt":"2026-11-18T02:00:00Z"}""")
        assertThat(openAlertTypes()).containsExactly("BACKUP_FAILED", "BACKUP_STALE", "CLOCK_SKEW", "DISK_LOW")

        clock.advance(Duration.ofMinutes(1))
        heartbeat(device.token, backup = """{"completedAt":"2026-11-20T05:00:00Z","status":"SUCCESS"}""")
        assertThat(openAlertTypes()).isEmpty()
        assertThat(jdbc.sql("select count(*) from monitoring_alert").query(Long::class.java).single()).isEqualTo(4)
    }

    @Test
    fun `silent servers go offline and come back with the next heartbeat`() {
        val owner = createStaff("owner")
        val device = pair(owner, newBranch(owner, newClinic(owner)))
        heartbeat(device.token)

        clock.advance(Duration.ofMinutes(10))
        evaluator.evaluate()
        assertThat(openAlertTypes()).isEmpty()

        clock.advance(Duration.ofMinutes(1))
        evaluator.evaluate()
        evaluator.evaluate()
        assertThat(openAlertTypes()).containsExactly("SERVER_OFFLINE")

        heartbeat(device.token)
        assertThat(openAlertTypes()).isEmpty()
    }

    @Test
    fun `paired servers that never report also raise offline, suspended branches are not monitored`() {
        val owner = createStaff("owner")
        val clinicId = newClinic(owner)
        val branchId = newBranch(owner, clinicId)
        pair(owner, branchId)

        clock.advance(Duration.ofMinutes(11))
        evaluator.evaluate()
        assertThat(openAlertTypes()).containsExactly("SERVER_OFFLINE")

        mockMvc.post("/api/v1/branches/$branchId/status") {
            with(asStaff(owner)); contentType = MediaType.APPLICATION_JSON; content = """{"status":"SUSPENDED","version":0}"""
        }
        evaluator.evaluate()
        assertThat(openAlertTypes()).isEmpty()
    }

    @Test
    fun `dashboard lists every live branch with server status, problems first`() {
        val owner = createStaff("owner")
        val support = createStaff("support", role = VendorRole.SUPPORT)
        val clinicId = newClinic(owner)
        val online = pair(owner, newBranch(owner, clinicId, "Online"))
        val offline = pair(owner, newBranch(owner, clinicId, "Offline"))
        newBranch(owner, clinicId, "Unpaired")
        heartbeat(offline.token)
        clock.advance(Duration.ofMinutes(20))
        heartbeat(online.token)
        evaluator.evaluate()

        mockMvc.get("/api/v1/monitoring/servers") { with(asStaff(support)) }.andExpect {
            status { isOk() }
            jsonPath("$.servers.length()") { value(3) }
            jsonPath("$.servers[0].branchName") { value("Offline") }
            jsonPath("$.servers[0].status") { value("OFFLINE") }
            jsonPath("$.servers[0].openAlerts[0]") { value("SERVER_OFFLINE") }
            jsonPath("$.servers[1].status") { value("ONLINE") }
            jsonPath("$.servers[1].appVersion") { value("1.0.1") }
            jsonPath("$.servers[2].status") { value("NOT_PAIRED") }
            jsonPath("$.counts.ONLINE") { value(1) }
            jsonPath("$.openAlertCount") { value(1) }
        }
        mockMvc.get("/api/v1/monitoring/servers?status=ONLINE") { with(asStaff(support)) }.andExpect {
            jsonPath("$.servers.length()") { value(1) }
        }
        mockMvc.get("/api/v1/monitoring/alerts") { with(asStaff(support)) }.andExpect {
            jsonPath("$.items[0].type") { value("SERVER_OFFLINE") }
            jsonPath("$.items[0].branchName") { value("Offline") }
        }
    }

    @Test
    fun `invalid heartbeat payload is rejected`() {
        val owner = createStaff("owner")
        val device = pair(owner, newBranch(owner, newClinic(owner)))

        mockMvc.post("/api/device/v1/heartbeats") {
            header("Authorization", "Bearer ${device.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"heartbeatId":"${UUID.randomUUID()}","sentAt":"2026-11-20T06:00:00Z","appVersion":"1","uptimeSeconds":-1,
                "disks":[{"mount":"C:","totalBytes":10,"freeBytes":20}]}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.fieldErrors.length()") { value(2) }
        }
    }
}
