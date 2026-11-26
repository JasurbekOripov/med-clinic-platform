package uz.medclinic.clinic.visits

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import uz.medclinic.clinic.visits.domain.ItemStatus
import uz.medclinic.clinic.visits.domain.OrderedService
import uz.medclinic.clinic.visits.domain.Visit
import uz.medclinic.clinic.visits.domain.VisitAgent
import uz.medclinic.clinic.visits.domain.VisitDiscount
import uz.medclinic.platform.domain.DomainException
import uz.medclinic.platform.domain.ValidationException
import java.time.Instant
import java.util.UUID

class VisitDomainTest {
    private val now = Instant.parse("2026-11-26T05:00:00Z")
    private val staff = UUID.randomUUID()
    private val pensioner = VisitDiscount(UUID.randomUUID(), "Nafaqaxoʻr", 20)

    private fun service(price: Long) = OrderedService(UUID.randomUUID(), "Xizmat $price", "Guruh", null, null, price)

    private fun visit(vararg prices: Long, discount: VisitDiscount? = null): Visit =
        prices.fold(Visit(UUID.randomUUID(), 1, UUID.randomUUID(), discount, null, emptyList(), staff, now, now, 0)) { v, p ->
            v.add(service(p), UUID.randomUUID(), staff, now)
        }

    @Test
    fun `discount is applied per service, rounded to whole sum, and re-prices only unpaid services`() {
        val v = visit(150_000, 12_345, discount = pensioner)
        assertThat(v.items.map { it.discount }).containsExactly(30_000, 2_469)
        assertThat(v.total).isEqualTo(150_000 + 12_345 - 30_000 - 2_469)

        val paid = v.markPaid(setOf(v.items[0].id), UUID.randomUUID(), now)
        val noDiscount = paid.withDiscount(null, now)
        assertThat(noDiscount.items.map { it.discount }).containsExactly(30_000, 0)
        assertThat(noDiscount.paid).isEqualTo(120_000)
        assertThat(noDiscount.unpaid).isEqualTo(12_345)
    }

    @Test
    fun `only unpaid services can be cancelled, always with a reason`() {
        val v = visit(50_000, 40_000)
        val first = v.items[0].id
        assertThatThrownBy { v.cancel(first, " ", staff, now) }.isInstanceOf(ValidationException::class.java)
        val cancelled = v.cancel(first, "Bemor rad etdi", staff, now)
        assertThat(cancelled.items[0].status).isEqualTo(ItemStatus.CANCELLED)
        assertThat(cancelled.total).isEqualTo(40_000)

        val paid = v.markPaid(setOf(first), UUID.randomUUID(), now)
        assertThatThrownBy { paid.cancel(first, "Xato", staff, now) }
            .isInstanceOfSatisfying(DomainException::class.java) { assertThat(it.code).isEqualTo("visit.item_already_paid") }
    }

    @Test
    fun `a service is paid once, the referral is fixed after the first payment`() {
        val v = visit(50_000).withAgent(VisitAgent(UUID.randomUUID(), "Agent", 10_000), now)
        val item = v.items.single().id
        val paid = v.markPaid(setOf(item), UUID.randomUUID(), now)
        assertThatThrownBy { paid.markPaid(setOf(item), UUID.randomUUID(), now) }
            .isInstanceOfSatisfying(DomainException::class.java) { assertThat(it.code).isEqualTo("visit.item_not_payable") }
        assertThatThrownBy { paid.withAgent(null, now) }
            .isInstanceOfSatisfying(DomainException::class.java) { assertThat(it.code).isEqualTo("visit.agent_locked") }
    }

    @Test
    fun `medical staff see paid services and those the admin allowed`() {
        val v = visit(50_000, 40_000, 30_000)
        val (a, b, c) = v.items.map { it.id }
        val state = v.markPaid(setOf(a), UUID.randomUUID(), now).allow(setOf(b), "Shoshilinch", staff, now)
        assertThat(state.items.map { it.visibleToMedicalStaff }).containsExactly(true, true, false)
        assertThatThrownBy { state.allow(setOf(a), "x", staff, now) }.isInstanceOf(DomainException::class.java)
        assertThatThrownBy { state.allow(setOf(c), null, staff, now) }.isInstanceOf(ValidationException::class.java)
    }
}
