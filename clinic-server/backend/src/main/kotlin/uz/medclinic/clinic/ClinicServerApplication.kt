package uz.medclinic.clinic

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.scheduling.annotation.EnableScheduling

// Staff authenticate through the identity module; Spring Boot's default in-memory user must never exist.
@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
@ConfigurationPropertiesScan
@EnableScheduling
class ClinicServerApplication

fun main(args: Array<String>) {
    runApplication<ClinicServerApplication>(*args)
}
