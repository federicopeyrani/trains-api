package com.federicopeyrani.trainapis.controller

import com.federicopeyrani.trainapis.model.DestinationStop
import com.federicopeyrani.trainapis.model.OriginStop
import com.federicopeyrani.trainapis.model.Station
import com.federicopeyrani.trainapis.model.Timing
import com.federicopeyrani.trainapis.model.Trip
import com.federicopeyrani.trainapis.trains.TripsService
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

@WebFluxTest(TrainsController::class)
class TrainsControllerTests(
    @Autowired private val webTestClient: WebTestClient,
) {

    @MockkBean
    lateinit var tripsService: TripsService

    @Test
    fun `should encode as JSON`() = runTest {
        coEvery { tripsService.getDepartures("S01700", any()) } returns flowOf(
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
