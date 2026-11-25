package uz.medclinic.vendor.shared.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Vendor-specific OpenAPI metadata; clock, errors and SPA serving come from `libs/spring-support`. */
@Configuration(proxyBeanMethods = false)
class PlatformConfig {
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
