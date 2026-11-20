package uz.medclinic.vendor.shared.web

import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.resource.PathResourceResolver
import java.time.Duration

/**
 * Serves the bundled console: hashed `/assets/` files are cached for a year; every other existing file is
 * served as-is; client-side routes (no file extension, not an API path) fall back to `index.html`, which is
 * never cached so a new release is picked up immediately.
 */
@Configuration(proxyBeanMethods = false)
class SpaWebConfig : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/assets/**")
            .addResourceLocations("classpath:/static/assets/")
            .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())

        registry.addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
            .setCacheControl(CacheControl.noCache())
            .resourceChain(true)
            .addResolver(SpaFallbackResolver())
    }

    private class SpaFallbackResolver : PathResourceResolver() {
        override fun getResource(resourcePath: String, location: Resource): Resource? {
            super.getResource(resourcePath, location)?.let { return it }
            val isClientRoute = !resourcePath.substringAfterLast('/').contains('.') &&
                RESERVED_PREFIXES.none { resourcePath == it || resourcePath.startsWith("$it/") }
            return if (isClientRoute) location.createRelative(INDEX).takeIf(Resource::isReadable) else null
        }
    }

    private companion object {
        const val INDEX = "index.html"
        val RESERVED_PREFIXES = listOf("api", "actuator", "v3", "swagger-ui", "assets", "error")
    }
}
