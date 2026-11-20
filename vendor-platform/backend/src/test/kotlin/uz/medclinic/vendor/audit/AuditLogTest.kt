package uz.medclinic.vendor.audit

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import uz.medclinic.vendor.identity.domain.VendorRole
import uz.medclinic.vendor.support.IntegrationTest

class AuditLogTest : IntegrationTest() {
    @Test
    fun `audit rows cannot be changed or deleted`() {
        createStaff("owner")
        login("owner")

        assertThatThrownBy { jdbc.sql("update audit_log set action = 'x'").update() }.hasMessageContaining("append-only")
        assertThatThrownBy { jdbc.sql("delete from audit_log").update() }.hasMessageContaining("append-only")
    }

    @Test
    fun `only owners can read the audit log`() {
        val support = createStaff("support", role = VendorRole.SUPPORT)

        mockMvc.get("/api/v1/audit") { with(asStaff(support)) }.andExpect { status { isForbidden() } }
    }
}
