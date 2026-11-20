package uz.medclinic.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class TashkentTimeTest {
    @Test
    fun `day boundaries are UTC plus five`() {
        val day = LocalDate.of(2026, 11, 20)
        assertThat(TashkentTime.startOfDay(day)).isEqualTo(Instant.parse("2026-11-19T19:00:00Z"))
        assertThat(TashkentTime.endOfDayExclusive(day)).isEqualTo(Instant.parse("2026-11-20T19:00:00Z"))
        assertThat(TashkentTime.dateOf(Instant.parse("2026-11-20T18:59:59Z"))).isEqualTo(day)
        assertThat(TashkentTime.dateOf(Instant.parse("2026-11-20T19:00:00Z"))).isEqualTo(day.plusDays(1))
    }
}
