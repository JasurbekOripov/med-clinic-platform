package uz.medclinic.vendor

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter

class ModularityTest {
    private val modules = ApplicationModules.of(VendorPlatformApplication::class.java)

    @Test
    fun `module boundaries are respected`() {
        modules.verify()
    }

    @Test
    fun `writes module documentation`() {
        Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml()
    }
}
