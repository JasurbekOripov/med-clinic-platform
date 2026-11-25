package uz.medclinic.clinic.shared

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI = OpenAPI().info(Info().title("Clinic Server API").version("v1"))
}
