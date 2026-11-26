package com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno

import com.federicopeyrani.trainapis.mockEngine
import com.federicopeyrani.trainapis.shared.infrastructure.cache.CacheConfig
import com.federicopeyrani.trainapis.shared.infrastructure.time.ClockConfig
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.runBlocking
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The cache TTLs come from `providers.viaggiatreno.cache.*`, and reach the API wrappers built by Spring.
 */
class ViaggiaTrenoCacheTtlsTests {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(ViaggiaTrenoConfig::class.java, CacheConfig::class.java, ClockConfig::class.java)
        .withBean(ViaggiaTrenoTrainsApiWrapper::class.java)
        .withBean(HttpClientEngine::class.java, { mockEngine() })

    @Test
    fun `uses the default TTLs when none is configured`() {
        contextRunner.run { context ->
            val board = runBlocking { context.getBean(ViaggiaTrenoTrainsApiWrapper::class.java).getDepartures("S01700", BOARD_TIME) }
            assertEquals(10.minutes, board.ttl)
        }
    }

    @Test
    fun `uses the configured TTLs`() {
        contextRunner
            .withPropertyValues("providers.viaggiatreno.cache.departures=45s", "providers.viaggiatreno.cache.train=2m")
            .run { context ->
                val properties = context.getBean(ViaggiaTrenoProperties::class.java)
                assertEquals(java.time.Duration.ofMinutes(2), properties.cache.train)
                assertEquals(java.time.Duration.ofMinutes(60), properties.cache.stationSearch)

                val board = runBlocking { context.getBean(ViaggiaTrenoTrainsApiWrapper::class.java).getDepartures("S01700", BOARD_TIME) }
                assertEquals(45.seconds, board.ttl)
            }
    }

    private companion object {

        // The departure board recorded in the fixtures
        val BOARD_TIME = Instant.parse("2026-01-23T21:20:00Z")
    }
}
