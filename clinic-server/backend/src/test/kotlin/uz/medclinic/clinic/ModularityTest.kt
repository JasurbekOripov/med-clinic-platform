package uz.medclinic.clinic

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

class ModularityTest {
    @Test
    fun `module boundaries are respected`() {
        ApplicationModules.of(ClinicServerApplication::class.java).verify()
    }
}
