package uz.medclinic.vendor.identity.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("vendor.security")
data class IdentityProperties(
    /** Hard upper bound of a login session regardless of activity. */
    val sessionAbsoluteTimeout: Duration = Duration.ofHours(12),
    /** `Secure` cookie attribute; only disable for local http development. */
    val sessionCookieSecure: Boolean = true,
    val bootstrapOwner: BootstrapOwner = BootstrapOwner(),
) {
    data class BootstrapOwner(val login: String = "", val password: String = "", val fullName: String = "Owner")
}
