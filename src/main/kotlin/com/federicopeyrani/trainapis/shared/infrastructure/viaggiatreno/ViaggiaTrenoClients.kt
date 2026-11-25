package com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno

import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.api.StationsApi
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.api.TrainsApi
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Factories for the generated ViaggiaTreno clients, usable with or without Spring.
 *
 * @param engine HTTP engine to use; `null` selects Ktor's default engine (tests pass a `MockEngine`)
 */
object ViaggiaTrenoClients {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val configure: (HttpClientConfig<*>) -> Unit = {
        it.install(ContentNegotiation) { json(json) }
        it.install(Logging) { level = LogLevel.INFO }
    }

    fun trainsApi(engine: HttpClientEngine? = null) = TrainsApi(httpClientEngine = engine, httpClientConfig = configure)

    fun stationsApi(engine: HttpClientEngine? = null) = StationsApi(httpClientEngine = engine, httpClientConfig = configure)
}
