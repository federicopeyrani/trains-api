package com.federicopeyrani.trainapis.shared.infrastructure.web

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.web.reactive.config.CorsRegistry
import org.springframework.web.reactive.config.WebFluxConfigurer

/**
 * Lets browser pages from other origins (such as the debug portal in `portal/`, served on another port) call the API.
 * The API is read-only and uses no credentials, so any origin is allowed unless `api.cors.allowed-origins` (a
 * comma-separated list of origin patterns) says otherwise.
 */
@Configuration
class CorsConfig(
    @Value("\${api.cors.allowed-origins:*}") private val allowedOrigins: String,
) : WebFluxConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOriginPatterns(*allowedOrigins.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray())
            .allowedMethods(HttpMethod.GET.name())
            .allowCredentials(false)
    }
}
