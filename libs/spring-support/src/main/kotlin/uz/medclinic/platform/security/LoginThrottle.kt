package uz.medclinic.platform.security

import org.springframework.scheduling.annotation.Scheduled
import uz.medclinic.platform.domain.DomainException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Brute-force protection for staff login: [maxPerLogin] failures for one login or [maxPerIp] failures from one
 * IP inside [window] block further attempts for [lockout]. In memory: correct for the single-instance VPS
 * deployment; move to the database or Redis before running several instances.
 */

class LoginThrottle(private val clock: Clock) {
    private val maxPerLogin = 5
    private val maxPerIp = 20
    private val window: Duration = Duration.ofMinutes(15)
    private val lockout: Duration = Duration.ofMinutes(15)

    private val byLogin = ConcurrentHashMap<String, Attempts>()
    private val byIp = ConcurrentHashMap<String, Attempts>()

    private data class Attempts(val windowStart: Instant, val failures: Int, val blockedUntil: Instant?)

    fun checkAllowed(login: String, ip: String?) {
        val now = clock.instant()
        val blockedUntil = listOfNotNull(byLogin[key(login)], ip?.let(byIp::get))
            .mapNotNull { it.blockedUntil }
            .filter { it.isAfter(now) }
            .maxOrNull() ?: return
        val retryAfter = Duration.between(now, blockedUntil).toSeconds().coerceAtLeast(1)
        throw DomainException("auth.too_many_attempts", DomainException.Kind.TOO_MANY_REQUESTS, mapOf("retryAfterSeconds" to retryAfter))
    }

    fun recordFailure(login: String, ip: String?) {
        val now = clock.instant()
        byLogin.compute(key(login)) { _, current -> next(current, now, maxPerLogin) }
        ip?.let { byIp.compute(it) { _, current -> next(current, now, maxPerIp) } }
    }

    fun recordSuccess(login: String) {
        byLogin.remove(key(login))
    }

    /** Forgets all counters (tests and operational resets). */
    fun clear() {
        byLogin.clear()
        byIp.clear()
    }

    @Scheduled(fixedDelay = 10, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    fun purgeExpired() {
        val now = clock.instant()
        val expired = { a: Attempts -> a.windowStart.plus(window).isBefore(now) && (a.blockedUntil == null || a.blockedUntil.isBefore(now)) }
        byLogin.values.removeIf(expired)
        byIp.values.removeIf(expired)
    }

    private fun next(current: Attempts?, now: Instant, limit: Int): Attempts {
        val inWindow = current != null && !current.windowStart.plus(window).isBefore(now)
        val base = if (inWindow) current else Attempts(now, 0, current?.blockedUntil)
        val failures = base.failures + 1
        // Reaching the limit starts a lockout and a fresh counter for the time after it.
        return if (failures >= limit) Attempts(now, 0, now.plus(lockout)) else base.copy(failures = failures)
    }

    private fun key(login: String) = login.trim().lowercase()
}
