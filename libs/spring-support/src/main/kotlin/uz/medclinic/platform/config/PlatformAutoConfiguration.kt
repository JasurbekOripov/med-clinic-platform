package uz.medclinic.platform.config

import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import org.springdoc.core.utils.SpringDocUtils
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import tools.jackson.databind.JsonNode
import uz.medclinic.platform.security.LoginThrottle
import uz.medclinic.platform.web.ApiExceptionHandler
import uz.medclinic.platform.web.ProblemWriter
import uz.medclinic.platform.web.RequestIdFilter
import uz.medclinic.platform.web.SpaWebConfig
import java.time.Clock

/**
 * Registers the shared building blocks in every application that has this library on the classpath:
 * problem+json error handling, request ids, the SPA resource resolver and a UTC [Clock].
 */
@AutoConfiguration
@Import(ApiExceptionHandler::class, ProblemWriter::class, RequestIdFilter::class, SpaWebConfig::class)
class PlatformAutoConfiguration {
    /** Inject [Clock] everywhere instead of calling `Instant.now()`, so time is testable. */
    @Bean
    @ConditionalOnMissingBean
    fun clock(): Clock = Clock.systemUTC()

    /** Brute-force protection for staff login (in memory; one instance per server). */
    @Bean
    @ConditionalOnMissingBean
    fun loginThrottle(clock: Clock): LoginThrottle = LoginThrottle(clock)

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(OpenApiCustomizer::class)
    class OpenApiSupport {
        init {
            // Free-form JSON (audit snapshots, alert details) is documented as a plain object instead of
            // Jackson's JsonNode internals, which also keeps the exported contract stable between runs.
            SpringDocUtils.getConfig().replaceWithSchema(JsonNode::class.java, ObjectSchema())
        }

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
    }
}
