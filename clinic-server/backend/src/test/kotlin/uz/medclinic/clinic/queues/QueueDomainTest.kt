package uz.medclinic.clinic.queues

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import uz.medclinic.clinic.queues.domain.Queue
import uz.medclinic.clinic.queues.domain.QueueDetails
import uz.medclinic.clinic.queues.domain.QueueRouter
import uz.medclinic.clinic.queues.domain.Routable
import uz.medclinic.clinic.queues.domain.Ticket
import uz.medclinic.platform.domain.ValidationException
import java.time.Instant
import java.util.UUID

class QueueDomainTest {
    private val now = Instant.parse("2026-11-26T05:00:00Z")
    private val doctor = UUID.randomUUID()
    private val ecg = UUID.randomUUID()
    private val blood = UUID.randomUUID()
    private val consultation = UUID.randomUUID()

    private fun queue(doctorId: UUID?, services: Set<UUID>, active: Boolean = true) =
        Queue(UUID.randomUUID(), QueueDetails("Q", "A", null, doctorId, services, active), now, now, null, 0)

    @Test
    fun `the chosen doctor's queue wins, otherwise the service's queue, otherwise no slip`() {
        val cardiologist = queue(doctor, emptySet())
        val lab = queue(null, setOf(blood))
        val functional = queue(null, setOf(ecg))
        val items = listOf(
            Routable(UUID.randomUUID(), consultation, doctor),
            Routable(UUID.randomUUID(), ecg, doctor), // ECG ordered with the cardiologist: done in the cardiologist's room
            Routable(UUID.randomUUID(), blood, null),
            Routable(UUID.randomUUID(), UUID.randomUUID(), null), // e.g. a certificate: no room
        )
        val routed = QueueRouter.route(items, listOf(cardiologist, lab, functional))
        assertThat(routed[cardiologist.id]).containsExactly(items[0], items[1])
        assertThat(routed[lab.id]).containsExactly(items[2])
        assertThat(routed).doesNotContainKey(functional.id).hasSize(2)
    }

    @Test
    fun `inactive queues receive nothing`() {
        val lab = queue(null, setOf(blood), active = false)
        assertThat(QueueRouter.route(listOf(Routable(UUID.randomUUID(), blood, null)), listOf(lab))).isEmpty()
    }

    @Test
    fun `labels and validation`() {
        assertThat(Ticket.label("A", 7)).isEqualTo("A-007")
        assertThat(Ticket.label("UZ", 1234)).isEqualTo("UZ-1234")
        assertThat(QueueDetails.of(" Laboratoriya ", "l", "12", null, setOf(blood), true).prefix).isEqualTo("L")
        assertThatThrownBy { QueueDetails.of("", "1", null, null, emptySet(), true) }
            .isInstanceOfSatisfying(ValidationException::class.java) { ex ->
                assertThat(ex.violations.map { it.field }).containsExactlyInAnyOrder("name", "prefix", "serviceIds")
            }
    }
}
