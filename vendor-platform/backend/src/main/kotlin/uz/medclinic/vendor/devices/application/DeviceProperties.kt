package uz.medclinic.vendor.devices.application

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("vendor.devices")
data class DeviceProperties(
    val pairingCodeTtl: Duration = Duration.ofHours(24),
    /** Interval the Clinic Server is told to use for heartbeats. */
    val heartbeatInterval: Duration = Duration.ofSeconds(60),
    /** `last_seen_at` is written at most this often per device to keep heartbeat traffic cheap. */
    val lastSeenWriteInterval: Duration = Duration.ofSeconds(30),
)
