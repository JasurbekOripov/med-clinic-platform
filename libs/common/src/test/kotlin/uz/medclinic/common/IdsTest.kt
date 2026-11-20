package uz.medclinic.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IdsTest {
    @Test
    fun `generates version 7 ids that are unique and time ordered`() {
        val ids = List(1_000) { Ids.newId() }

        assertThat(ids).allSatisfy { assertThat(it.version()).isEqualTo(7) }
        assertThat(ids.toSet()).hasSize(ids.size)
        assertThat(ids.map { it.toString() }).isSorted()
    }
}
