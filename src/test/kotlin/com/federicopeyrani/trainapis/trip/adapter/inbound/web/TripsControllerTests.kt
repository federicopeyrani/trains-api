package com.federicopeyrani.trainapis.trip.adapter.inbound.web

import com.federicopeyrani.trainapis.shared.domain.ProviderUnavailableException
import com.federicopeyrani.trainapis.shared.domain.Station
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetAllTripsUseCase
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetDeparturesUseCase
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetRunUseCase
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetTripsUseCase
import com.federicopeyrani.trainapis.trip.domain.DestinationStop
import com.federicopeyrani.trainapis.trip.domain.OriginStop
import com.federicopeyrani.trainapis.trip.domain.RunId
import com.federicopeyrani.trainapis.trip.domain.RunNotFoundException
import com.federicopeyrani.trainapis.trip.domain.Timing
import com.federicopeyrani.trainapis.trip.domain.Trip
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDateTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.context.annotation.Bean
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.Test

@WebFluxTest(TripsController::class)
class TripsControllerTests(
    @Autowired private val webTestClient: WebTestClient,
) {

    companion object {

        private val departure = Trip(
            from = OriginStop(
                station = Station(
                    id = "S01700",
                    name = "MILANO CENTRALE",
                ),
                departureTime = Timing(
                    scheduled = LocalDateTime(2026, 1, 23, 22, 15),
                    expected = LocalDateTime(2026, 1, 23, 22, 15),
                    actual = LocalDateTime(2026, 1, 23, 22, 18),
                ),
            ),
            to = DestinationStop(
                station = Station(
                    id = "S00452",
                    name = "TORINO LINGOTTO",
                ),
                arrivalTime = Timing(
                    scheduled = LocalDateTime(2026, 1, 23, 23, 15),
                    expected = LocalDateTime(2026, 1, 23, 23, 20),
                    actual = null,
                ),
            ),
            lastUpdate = LocalDateTime(2026, 1, 23, 23, 15),
            run = null,
        )
    }

    /**
     * The use cases are `fun interface`s, so plain lambdas stand in for the application layer.
     */
    @TestConfiguration
    class UseCases {

        @Bean
        fun getDepartures() = GetDeparturesUseCase { from, _ ->
            if (from == "S01700") flowOf(departure) else emptyFlow()
        }

        @Bean
        fun getTrips() = GetTripsUseCase { _, _, _ -> emptyFlow() }

        @Bean
        fun getAllTrips() = GetAllTripsUseCase { _, _, _ -> emptyFlow() }

        @Bean
        fun getRun() = GetRunUseCase { id ->
            when (id) {
                "S01700_1_0" -> throw RunNotFoundException(RunId.parse(id))
                "S01700_2_0" -> throw ProviderUnavailableException("ViaggiaTreno train status returned HTTP 403")
                else -> RunId.parse(id).let { error("Unexpected run $it") }
            }
        }
    }

    @Test
    fun `should encode as JSON`() {
        val expectedJson = javaClass.classLoader
            .getResource("expected/trips.json")!!
            .readText()

        webTestClient
            .get()
            .uri("/departures/S01700")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .json(expectedJson, JsonCompareMode.STRICT)
    }

    @Test
    fun `maps domain failures to problem responses`() {
        mapOf(
            "not-a-run-id" to 400,
            "S01700_1_0" to 404,
            "S01700_2_0" to 503,
        ).forEach { (runId, status) ->
            webTestClient
                .get()
                .uri("/runs/$runId")
                .exchange()
                .expectStatus()
                .isEqualTo(status)
                .expectBody()
                .jsonPath("$.status").isEqualTo(status)
        }
    }
}
