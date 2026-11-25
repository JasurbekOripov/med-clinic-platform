package uz.medclinic.clinic.vendorlink.infrastructure

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * Counts ERROR and WARN log events for the heartbeat (only numbers leave the clinic, never messages,
 * which could contain personal data).
 */
@Component
class ErrorCounter(private val clock: Clock) {
    private val errors = AtomicLong()
    private val warnings = AtomicLong()

    @Volatile
    private var since: Instant = clock.instant()

    data class Snapshot(val since: Instant, val errors: Long, val warnings: Long)

    @PostConstruct
    fun register() {
        val context = LoggerFactory.getILoggerFactory() as? LoggerContext ?: return
        val appender = object : AppenderBase<ILoggingEvent>() {
            override fun append(event: ILoggingEvent) {
                when (event.level) {
                    Level.ERROR -> errors.incrementAndGet()
                    Level.WARN -> warnings.incrementAndGet()
                    else -> Unit
                }
            }
        }
        appender.context = context
        appender.name = "heartbeat-error-counter"
        appender.start()
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender)
    }

    /** Counts since the previous call; the window restarts. */
    fun takeSnapshot(): Snapshot {
        val now = clock.instant()
        val snapshot = Snapshot(since, errors.getAndSet(0), warnings.getAndSet(0))
        since = now
        return snapshot
    }
}
