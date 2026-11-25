package com.federicopeyrani.trainapis.trip.adapter.inbound.web

import com.federicopeyrani.trainapis.trip.domain.TrainNumber
import com.federicopeyrani.trainapis.shared.domain.StationId
import com.federicopeyrani.trainapis.shared.domain.ProviderUnavailableException
import com.federicopeyrani.trainapis.shared.domain.Station
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetAllTripsUseCase
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetDeparturesUseCase
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetRunUseCase
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetTripsUseCase
import com.federicopeyrani.trainapis.trip.domain.DestinationStop
import com.federicopeyrani.trainapis.trip.domain.IntermediateStop
import com.federicopeyrani.trainapis.trip.domain.OriginStop
import com.federicopeyrani.trainapis.trip.domain.Run
import com.federicopeyrani.trainapis.trip.domain.RunId
import com.federicopeyrani.trainapis.trip.domain.RunNotFoundException
import com.federicopeyrani.trainapis.trip.domain.RunStatus
import com.federicopeyrani.trainapis.trip.domain.StopStatus
import com.federicopeyrani.trainapis.trip.domain.Timing
import com.federicopeyrani.trainapis.trip.domain.Train
import com.federicopeyrani.trainapis.trip.domain.Trip
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.test.json.JsonAssert
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.returnResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * HTTP contract of the trips endpoints: payload encoding, request binding and error responses. The application layer is
 * replaced by lambdas, so these tests guard the web configuration (codecs, binders, exception handling) on their own.
 */
@WebFluxTest(TripsController::class)
class TripsControllerTests(
    @Autowired private val webTestClient: WebTestClient,
    @Autowired private val requests: Requests,
) {

    companion object {

        private fun at(hour: Int, minute: Int) = LocalDateTime(2026, 1, 23, hour, minute).toInstant(TimeZone.UTC)

        private val centrale = Station(StationId("S01700"), "Milano Centrale")
        private val rogoredo = Station(StationId("S01820"), "Milano Rogoredo")
        private val lingotto = Station(StationId("S00452"), "Torino Lingotto")

        /**
         * A delayed run that has left its origin and reached its intermediate stop: every optional field is set.
         */
        val run = Run(
            id = RunId(StationId("S01700"), TrainNumber(2185), 1769122800000),
            train = Train(TrainNumber(2185), centrale, lingotto),
            status = RunStatus.DELAYED,
            origin = OriginStop(centrale, StopStatus.DEPARTED, Timing(at(21, 15), at(21, 15), at(21, 18))),
            destination = DestinationStop(lingotto, StopStatus.NOT_YET_ARRIVED, Timing(at(23, 15), at(23, 20))),
            stops = listOf(
                IntermediateStop(
                    station = rogoredo,
                    status = StopStatus.ARRIVED,
                    arrivalTime = Timing(at(21, 25), at(21, 28), at(21, 29)),
                    departureTime = Timing(at(21, 27), at(21, 30)),
                ),
            ),
            lastUpdate = at(21, 30),
            lastUpdateFromProvider = at(21, 29),
        )

        /**
         * One trip with its run and one known only from the board: optional fields must be omitted, not `null`.
         */
        val trips = listOf(
            Trip(
                from = OriginStop(rogoredo, StopStatus.NOT_YET_DEPARTED, run.stops.single().departureTime),
                to = run.destination,
                lastUpdate = at(21, 30),
                lastUpdateFromProvider = at(21, 29),
                run = run,
            ),
            Trip(
                from = OriginStop(centrale, departureTime = Timing(at(22, 15))),
                to = DestinationStop(lingotto, arrivalTime = Timing(at(23, 15))),
                lastUpdate = at(21, 30),
            ),
        )
    }

    /**
     * Arguments received by the use cases, so tests can check how requests are bound.
     */
    class Requests : MutableList<List<Any?>> by mutableListOf()

    @TestConfiguration
    class UseCases {

        @Bean
        fun requests() = Requests()

        @Bean
        fun getDepartures(requests: Requests) = GetDeparturesUseCase { from, date ->
            requests += listOf(from, date)
            if (from == StationId("S01700")) trips.asFlow() else emptyFlow()
        }

        @Bean
        fun getTrips(requests: Requests) = GetTripsUseCase { from, to, date ->
            requests += listOf(from, to, date)
            emptyFlow()
        }

        @Bean
        fun getAllTrips(requests: Requests) = GetAllTripsUseCase { from, to, date ->
            requests += listOf(from, to, date)
            emptyFlow()
        }

        @Bean
        fun getRun() = GetRunUseCase { id ->
            when (id) {
                "S01700_2185_1769122800000" -> run
                "S01700_1_0" -> throw RunNotFoundException(RunId.parse(id))
                "S01700_2_0" -> throw ProviderUnavailableException("ViaggiaTreno train status returned HTTP 403")
                else -> RunId.parse(id).let { error("Unexpected run $it") }
            }
        }
    }

    private fun expected(name: String) = javaClass.getResource("/expected/$name")!!.readText()

    @Test
    fun `encodes trips as JSON`() {
        webTestClient.get().uri("/departures/S01700")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody().json(expected("trips.json"), JsonCompareMode.STRICT)
    }

    @Test
    fun `encodes a run as JSON`() {
        webTestClient.get().uri("/runs/S01700_2185_1769122800000")
            .exchange()
            .expectStatus().isOk
            .expectBody().json(expected("run.json"), JsonCompareMode.STRICT)
    }

    @Test
    fun `streams trips as server-sent events with the same encoding`() {
        val events = webTestClient.get().uri("/departures/S01700")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
            .returnResult<String>()
            .responseBody
            .collectList()
            .block()!!

        val json = "[${events.joinToString(",")}]"
        JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(expected("trips.json"), json)
    }

    @Test
    fun `binds dates as UTC and repeated query parameters as lists`() {
        val utc = Instant.parse("2026-01-23T21:15:00Z")

        listOf(
            "/departures/S01700?date=2026-01-23T21:15:00" to listOf(StationId("S01700"), utc),
            "/departures/S01700?date=2026-01-23T21:15" to listOf(StationId("S01700"), utc),
            "/departures/S01700" to listOf(StationId("S01700"), null),
            "/trips/S01700/S00452?date=2026-01-23T21:15:00" to listOf(StationId("S01700"), StationId("S00452"), utc),
            "/trips/all?from=S01700&from=S01820&to=S00452" to listOf(listOf(StationId("S01700"), StationId("S01820")), listOf(StationId("S00452")), null),
        ).forEach { (uri, arguments) ->
            requests.clear()
            webTestClient.get().uri(uri).exchange().expectStatus().isOk
            assertEquals(listOf(arguments), requests, uri)
        }
    }

    @ParameterizedTest
    @CsvSource(
        "/runs/not-a-run-id, 400, Invalid run id 'not-a-run-id'",
        "/runs/S01700_1_0, 404, Run 'S01700_1_0' not found",
        "/runs/S01700_2_0, 503, ViaggiaTreno train status returned HTTP 403",
    )
    fun `maps domain failures to problem responses`(uri: String, status: Int, detail: String) {
        webTestClient.get().uri(uri)
            .exchange()
            .expectStatus().isEqualTo(status)
            .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(status)
            .jsonPath("$.detail").isEqualTo(detail)
    }

    @ParameterizedTest
    @CsvSource(
        "/departures/S01700?date=tomorrow",
        "/trips/all?to=S00452",
    )
    fun `rejects malformed requests`(uri: String) {
        webTestClient.get().uri(uri)
            .exchange()
            .expectStatus().isBadRequest
    }
}
