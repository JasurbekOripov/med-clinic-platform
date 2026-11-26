package uz.medclinic.clinic.catalog

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import uz.medclinic.clinic.catalog.domain.DiscountDetails
import uz.medclinic.clinic.catalog.domain.DiscountMath
import uz.medclinic.clinic.catalog.domain.MAX_PRICE
import uz.medclinic.clinic.catalog.domain.ServiceDetails
import uz.medclinic.platform.domain.ValidationException
import java.util.UUID

class CatalogDomainTest {
    private val group = UUID.randomUUID()
    private val doctor = UUID.randomUUID()

    @Test
    fun `percent discounts round half up to whole sum with exact arithmetic`() {
        assertThat(DiscountMath.percentOf(150_000, 20)).isEqualTo(30_000)
        assertThat(DiscountMath.percentOf(12_345, 10)).isEqualTo(1_235) // 1234.5 → 1235
        assertThat(DiscountMath.percentOf(12_344, 10)).isEqualTo(1_234) // 1234.4 → 1234
        assertThat(DiscountMath.percentOf(99_999, 100)).isEqualTo(99_999)
        assertThat(DiscountMath.percentOf(0, 30)).isZero()
        assertThat(DiscountMath.percentOf(MAX_PRICE, 33)).isEqualTo(330_000_000)
    }

    @Test
    fun `doctor price overrides the base price only for that doctor`() {
        val consultation = ServiceDetails.of(group, "K-1", " Kardiolog  konsultatsiyasi ", 150_000, true, mapOf(doctor to 200_000), true)
        assertThat(consultation.name).isEqualTo("Kardiolog konsultatsiyasi")
        assertThat(consultation.priceFor(doctor)).isEqualTo(200_000)
        assertThat(consultation.priceFor(UUID.randomUUID())).isEqualTo(150_000)
        assertThat(consultation.priceFor(null)).isEqualTo(150_000)
    }

    @Test
    fun `service validation reports every problem`() {
        assertThatThrownBy { ServiceDetails.of(null, "a b", " ", -1, false, mapOf(doctor to 5), true) }
            .isInstanceOfSatisfying(ValidationException::class.java) { ex ->
                assertThat(ex.violations.map { it.field to it.code }).containsExactlyInAnyOrder(
                    "groupId" to "validation.not_null",
                    "code" to "validation.service_code",
                    "name" to "validation.not_blank",
                    "price" to "validation.range",
                    "doctorPrices" to "catalog.doctor_prices_not_per_doctor",
                )
            }
        assertThatThrownBy { ServiceDetails.of(group, null, "X", MAX_PRICE + 1, false, emptyMap(), true) }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `discount percent must be between 1 and 100`() {
        assertThat(DiscountDetails.of(" Nafaqaxoʻr ", 20, true).name).isEqualTo("Nafaqaxoʻr")
        listOf(0, 101, null).forEach { percent ->
            assertThatThrownBy { DiscountDetails.of("Nogironlik", percent, true) }.isInstanceOf(ValidationException::class.java)
        }
    }
}
