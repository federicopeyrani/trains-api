package com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno

import com.federicopeyrani.trainapis.shared.infrastructure.cache.Cache
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.api.StationsApi
import io.ktor.client.engine.HttpClientEngine
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Exposes the generated ViaggiaTreno clients as Spring beans.
 *
 * An [HttpClientEngine] bean is optional: when absent, Ktor's default engine is used.
 */
@Configuration
@EnableConfigurationProperties(ViaggiaTrenoProperties::class)
class ViaggiaTrenoConfig {

    @Bean
    fun trainsApi(engine: ObjectProvider<HttpClientEngine>) = ViaggiaTrenoClients.trainsApi(engine.ifAvailable)

    @Bean
    fun stationsApi(engine: ObjectProvider<HttpClientEngine>) = ViaggiaTrenoClients.stationsApi(engine.ifAvailable)

    @Bean
    fun stationRegistry(stationsApi: StationsApi, cache: Cache) = ViaggiaTrenoStationRegistry(stationsApi, cache)
}
