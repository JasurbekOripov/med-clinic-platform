package uz.medclinic.vendor.shared.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class PlatformConfig {
    /** Inject [Clock] everywhere instead of calling `Instant.now()`, so time is testable. */
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(Info().title("Vendor Platform API").version("v1"))
        .components(
            Components()
                .addSecuritySchemes(
                    "staffSession",
                    SecurityScheme().type(SecurityScheme.Type.APIKEY).`in`(SecurityScheme.In.COOKIE).name("VSESSION"),
                )
                .addSecuritySchemes(
                    "deviceToken",
                    SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer"),
                ),
        )
}
