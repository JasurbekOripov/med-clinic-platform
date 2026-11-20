package uz.medclinic.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Business calendar of the platform: days are Asia/Tashkent calendar days, storage is UTC. */
object TashkentTime {
    val ZONE: ZoneId = ZoneId.of("Asia/Tashkent")

    fun startOfDay(date: LocalDate): Instant = date.atStartOfDay(ZONE).toInstant()

    /** Exclusive end of [date]: the first instant of the following day. */
    fun endOfDayExclusive(date: LocalDate): Instant = startOfDay(date.plusDays(1))

    fun dateOf(instant: Instant): LocalDate = instant.atZone(ZONE).toLocalDate()
}
