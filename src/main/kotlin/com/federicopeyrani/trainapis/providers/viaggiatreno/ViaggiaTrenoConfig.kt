package com.federicopeyrani.trainapis.providers.viaggiatreno

import com.federicopeyrani.trainapis.data.Cache
import com.federicopeyrani.trainapis.providers.viaggiatreno.client.api.StationsApi
import com.federicopeyrani.trainapis.providers.viaggiatreno.client.api.TrainsApi
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring configuration for ViaggiaTreno API clients.
 *
 * Provides the generated OpenAPI client instances as Spring beans
 * for dependency injection.
 */
@Configuration
@EnableCaching
class ViaggiaTrenoConfig {

    companion object {

        private val cache = "viaggiatreno"
    }

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
    fun trainsApi(engine: HttpClientEngine?) =
        TrainsApi(httpClientEngine = engine) {
            it.install(ContentNegotiation) { json(json) }
            it.install(Logging) { level = LogLevel.INFO }
        }

    @Bean
    fun stationsApi(engine: HttpClientEngine?) =
        StationsApi(httpClientEngine = engine) {
            it.install(ContentNegotiation) { json(json) }
            it.install(Logging) { level = LogLevel.INFO }
        }

    @Bean
    fun CacheManager(): CacheManager = ConcurrentMapCacheManager(cache)

    @Bean
    fun Cache(factory: Cache.Factory): Cache = factory.create(cache)
}

