package uz.medclinic.clinic.vendorlink

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("clinic.vendor")
data class VendorLinkProperties(
    /** Key file for encrypting the device token at rest (created on first start, readable by the service only). */
    val secretKeyPath: String = "./data/secret.key",
    /** Allows `http://` vendor URLs (development only; production requires https). */
    val allowInsecureUrl: Boolean = false,
    val heartbeatInterval: Duration = Duration.ofSeconds(60),
    val connectTimeout: Duration = Duration.ofSeconds(10),
    val readTimeout: Duration = Duration.ofSeconds(20),
    /** Local clock may go back by at most this much (NTP corrections) before the clock guard blocks work. */
    val clockRollbackTolerance: Duration = Duration.ofMinutes(10),
    /** Difference to the vendor's clock that is reported as a warning. */
    val clockSkewWarning: Duration = Duration.ofMinutes(15),
    /** Development only: extra license public keys `keyId:base64,…` (honoured in dev/test profiles only). */
    val devKeyring: String = "",
)
