package com.federicopeyrani.trainapis.trip.adapter.inbound.web

import com.federicopeyrani.trainapis.shared.domain.Station
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetDeparturesUseCase
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetRunUseCase
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetTripsUseCase
import com.federicopeyrani.trainapis.trip.domain.DestinationStop
import com.federicopeyrani.trainapis.trip.domain.OriginStop
import com.federicopeyrani.trainapis.trip.domain.Timing
import com.federicopeyrani.trainapis.trip.domain.Trip
import com.ninjasquad.springmockk.MockkBean
import io.mockk.coEvery
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.Test

@WebFluxTest(TripsController::class)
class TripsControllerTests(
    @Autowired private val webTestClient: WebTestClient,
) {

    @MockkBean
    lateinit var getDeparturesUseCase: GetDeparturesUseCase

    @MockkBean
    lateinit var getTripsUseCase: GetTripsUseCase

    @MockkBean
    lateinit var getRunUseCase: GetRunUseCase

    @Test
    fun `should encode as JSON`() = runTest {
        coEvery { getDeparturesUseCase.getDepartures("S01700", any()) } returns flowOf(
            Trip(
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
        )

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
}
