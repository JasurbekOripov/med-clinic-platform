package uz.medclinic.vendor.devices.application

import org.springframework.stereotype.Component
import uz.medclinic.vendor.shared.domain.DomainException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Limits failed pairing claims per IP (10 per 15 min). 60-bit codes cannot be brute forced anyway; this keeps
 * noise and database load down. In memory: single-instance deployment.
 */
@Component
class ClaimThrottle(private val clock: Clock) {
    private val maxFailures = 10
    private val window: Duration = Duration.ofMinutes(15)
    private val failures = ConcurrentHashMap<String, MutableList<Instant>>()

    fun checkAllowed(ip: String?) {
        val list = failures[ip ?: UNKNOWN] ?: return
        val now = clock.instant()
        val recent = synchronized(list) { list.removeIf { it.plus(window).isBefore(now) }; list.size }
        if (recent >= maxFailures) {
            throw DomainException("pairing.too_many_attempts", DomainException.Kind.TOO_MANY_REQUESTS, mapOf("retryAfterSeconds" to window.toSeconds()))
        }
    }

    fun recordFailure(ip: String?) {
        val list = failures.computeIfAbsent(ip ?: UNKNOWN) { mutableListOf() }
        synchronized(list) { list += clock.instant() }
    }

    fun clear() = failures.clear()

    private companion object {
        const val UNKNOWN = "unknown"
    }
}
