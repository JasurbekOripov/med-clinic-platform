package uz.medclinic.vendor

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

// Staff authenticate through our own AuthService and devices through bearer tokens: the default in-memory
// user (with a generated password printed to the log) must never exist.
@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
@ConfigurationPropertiesScan
@EnableScheduling
class VendorPlatformApplication

fun main(args: Array<String>) {
    runApplication<VendorPlatformApplication>(*args)
}
