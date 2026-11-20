package uz.medclinic.vendor.support

import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import uz.medclinic.vendor.identity.domain.StaffAccount
import java.util.UUID

data class PairedDevice(val deviceId: UUID, val clinicId: UUID, val branchId: UUID, val token: String)

/** API-level fixtures shared by device/monitoring tests. */
abstract class TenantIntegrationTest : IntegrationTest() {
    protected fun newClinic(staff: StaffAccount, name: String = "Shifo Med"): UUID {
        val response = mockMvc.post("/api/v1/clinics") {
            with(asStaff(staff)); contentType = MediaType.APPLICATION_JSON
            content = """{"name":"$name","ownerName":"Vali Aliyev","phone":"901234567","region":"TASHKENT_CITY"}"""
        }.andReturn().response.contentAsString
        return UUID.fromString(body(response).path("id").asString())
    }

    protected fun newBranch(staff: StaffAccount, clinicId: UUID, name: String = "Markaziy"): UUID {
        val response = mockMvc.post("/api/v1/clinics/$clinicId/branches") {
            with(asStaff(staff)); contentType = MediaType.APPLICATION_JSON; content = """{"name":"$name"}"""
        }.andReturn().response.contentAsString
        return UUID.fromString(body(response).path("id").asString())
    }

    protected fun newPairingCode(staff: StaffAccount, branchId: UUID): String {
        val response = mockMvc.post("/api/v1/branches/$branchId/pairing-codes") { with(asStaff(staff)) }
            .andReturn().response.contentAsString
        return body(response).path("code").asString()
    }

    protected fun claim(code: String, hostname: String = "CLINIC-SRV") = mockMvc.post("/api/device/v1/pairing/claim") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"code":"$code","hostname":"$hostname","os":"Windows 11 Pro","appVersion":"1.0.0"}"""
    }

    protected fun pair(staff: StaffAccount, branchId: UUID, hostname: String = "CLINIC-SRV"): PairedDevice {
        val response = body(claim(newPairingCode(staff, branchId), hostname).andReturn().response.contentAsString)
        return PairedDevice(
            UUID.fromString(response.path("deviceId").asString()),
            UUID.fromString(response.path("clinicId").asString()),
            UUID.fromString(response.path("branchId").asString()),
            response.path("deviceToken").asString(),
        )
    }

    protected fun heartbeat(
        token: String,
        heartbeatId: UUID = UUID.randomUUID(),
        sentAt: String = clock.instant().toString(),
        freeBytes: Long = 200_000_000_000,
        installedLicenseId: UUID? = null,
        backup: String = "null",
    ) = mockMvc.post("/api/device/v1/heartbeats") {
        header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        content = """{"heartbeatId":"$heartbeatId","sentAt":"$sentAt","appVersion":"1.0.1","uptimeSeconds":3600,
            "disks":[{"mount":"C:","totalBytes":500000000000,"freeBytes":$freeBytes}],
            "lastBackup":$backup,"errors":{"since":"$sentAt","errorCount":0,"warningCount":2},
            "installedLicenseId":${installedLicenseId?.let { "\"$it\"" } ?: "null"}}"""
    }
}
