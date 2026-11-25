package com.federicopeyrani.trainapis.station.adapter.inbound.web

import com.federicopeyrani.trainapis.shared.domain.StationId
import com.federicopeyrani.trainapis.shared.domain.Station
import com.federicopeyrani.trainapis.station.application.port.inbound.SearchStationsUseCase
import com.federicopeyrani.trainapis.station.domain.GeoLocation
import com.federicopeyrani.trainapis.station.domain.StationDetails
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.context.annotation.Bean
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.Test

/**
 * HTTP contract of the stations endpoint, with the application layer replaced by a lambda.
 */
@WebFluxTest(StationsController::class)
class StationsControllerTests(
    @Autowired private val webTestClient: WebTestClient,
) {

    @TestConfiguration
    class UseCases {

        @Bean
        fun searchStations() = SearchStationsUseCase { query ->
            when (query) {
                "torino" -> flowOf(
                    StationDetails(Station(StationId("S00452"), "Torino Lingotto"), GeoLocation(45.0266, 7.657469)),
                    StationDetails(Station(StationId("S00450"), "Torino Stura"), location = null),
                )

                else -> emptyFlow()
            }
        }
    }

    @Test
    fun `encodes stations with their location when known`() {
        webTestClient.get().uri("/stations?query=torino")
            .exchange()
            .expectStatus().isOk
            .expectBody().json(
                """
                [
                  { "id": "S00452", "name": "Torino Lingotto", "location": { "latitude": 45.0266, "longitude": 7.657469 } },
                  { "id": "S00450", "name": "Torino Stura" }
                ]
                """,
                JsonCompareMode.STRICT,
            )
    }

    @Test
    fun `requires a query`() {
        webTestClient.get().uri("/stations")
            .exchange()
            .expectStatus().isBadRequest
    }
}
