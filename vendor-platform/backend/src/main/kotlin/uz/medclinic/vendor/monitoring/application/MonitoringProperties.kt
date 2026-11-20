package uz.medclinic.vendor.monitoring.application

import org.springframework.boot.context.properties.ConfigurationProperties
import uz.medclinic.vendor.monitoring.domain.HealthThresholds
import java.time.Duration

@ConfigurationProperties("vendor.monitoring")
data class MonitoringProperties(
    val offlineAfter: Duration = Duration.ofMinutes(10),
    val heartbeatRetention: Duration = Duration.ofDays(30),
    /** Disabled in tests, which drive the evaluator directly. */
    val evaluationEnabled: Boolean = true,
    val thresholds: HealthThresholds = HealthThresholds(),
)
