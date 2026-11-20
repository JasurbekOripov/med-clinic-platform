package uz.medclinic.vendor.shared.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.media.Schema
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class PlatformConfig {
    /** Inject [Clock] everywhere instead of calling `Instant.now()`, so time is testable. */
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    /**
     * Kotlin nullability is the source of truth: springdoc renders `String?` as `["string","null"]`; every
     * other property is non-null and therefore marked `required`, so generated TypeScript types are exact.
     */
    @Bean
    fun requiredNonNullableProperties(): OpenApiCustomizer = OpenApiCustomizer { api ->
        api.components?.schemas?.values?.forEach { schema ->
            val properties: Map<String, Schema<*>> = schema.properties ?: return@forEach
            val required = properties.filter { (_, p) -> !isNullable(p) }.keys.sorted()
            if (required.isNotEmpty()) schema.required = required
        }
    }

    private fun isNullable(p: Schema<*>): Boolean =
        p.types?.contains("null") == true || p.nullable == true ||
            p.oneOf?.any { it.types?.contains("null") == true } == true ||
            p.anyOf?.any { it.types?.contains("null") == true } == true

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
