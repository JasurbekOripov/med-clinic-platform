package uz.medclinic.vendor.identity.security

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import uz.medclinic.vendor.identity.application.StaffService
import uz.medclinic.platform.domain.DomainException

/** Creates the first OWNER from configuration on an empty installation; a no-op afterwards. */
@Component
class OwnerBootstrap(private val staff: StaffService, private val properties: IdentityProperties) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val owner = properties.bootstrapOwner
        if (owner.login.isBlank() || owner.password.isBlank()) return
        try {
            staff.createInitialOwner(owner.login, owner.fullName, owner.password)
                ?.let { log.info("Bootstrapped initial owner account '{}' (password change required)", it.login) }
        } catch (ex: DomainException) {
            // Another instance won the race, or the configured values are invalid: never block startup.
            log.warn("Initial owner bootstrap skipped: {}", ex.code)
        }
    }
}
