package com.federicopeyrani.trainapis.providers.viaggiatreno

import com.federicopeyrani.trainapis.providers.viaggiatreno.client.api.StationsApi
import com.federicopeyrani.trainapis.providers.viaggiatreno.client.api.TrainsApi
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring configuration for ViaggiaTreno API clients.
 *
 * Provides the generated OpenAPI client instances as Spring beans
 * for dependency injection.
 */
@Configuration
class ViaggiaTrenoConfig {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * Provides the TrainsApi client as a Spring bean.
     *
     * @return TrainsApi instance for train-related endpoints
     */
    @Bean
    fun trainsApi(): TrainsApi {
        return TrainsApi {
            it.install(ContentNegotiation) { json(json) }
            it.install(Logging) { level = LogLevel.ALL }
        }
    }

    @Bean
    fun stationsApi(): StationsApi {
        return StationsApi {
            it.install(ContentNegotiation) { json(json) }
            it.install(Logging) { level = LogLevel.ALL }
        }
    }
}

