package uz.medclinic.vendor.tenancy

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import uz.medclinic.platform.domain.ValidationException
import uz.medclinic.vendor.tenancy.api.TenantStatus
import uz.medclinic.vendor.tenancy.api.TenantStatus.ACTIVE
import uz.medclinic.vendor.tenancy.api.TenantStatus.ARCHIVED
import uz.medclinic.vendor.tenancy.api.TenantStatus.SUSPENDED
import uz.medclinic.vendor.tenancy.domain.ClinicDetails
import uz.medclinic.vendor.tenancy.domain.Region

class TenantStatusTest {
    @Test
    fun `allowed transitions`() {
        val allowed = TenantStatus.entries.flatMap { from -> TenantStatus.entries.filter(from::canTransitionTo).map { from to it } }

        assertThat(allowed).containsExactlyInAnyOrder(
            ACTIVE to SUSPENDED, ACTIVE to ARCHIVED, SUSPENDED to ACTIVE, SUSPENDED to ARCHIVED, ARCHIVED to ACTIVE,
        )
    }

    @Test
    fun `clinic details are normalised and all field problems are reported together`() {
        val details = ClinicDetails.of("  Shifo   Med ", " ", "Aliyev Vali", "90 123 45 67", null, Region.SAMARKAND, "  line1\nline2 ")
        assertThat(details.name).isEqualTo("Shifo Med")
        assertThat(details.legalName).isNull()
        assertThat(details.phone.e164).isEqualTo("+998901234567")
        assertThat(details.notes).isEqualTo("line1\nline2")

        assertThatThrownBy { ClinicDetails.of("", null, null, "123", null, null, null) }
            .isInstanceOfSatisfying(ValidationException::class.java) { ex ->
                assertThat(ex.violations.map { it.field to it.code }).containsExactlyInAnyOrder(
                    "name" to "validation.not_blank",
                    "ownerName" to "validation.not_blank",
                    "phone" to "validation.phone",
                    "region" to "validation.not_null",
                )
            }
    }
}
