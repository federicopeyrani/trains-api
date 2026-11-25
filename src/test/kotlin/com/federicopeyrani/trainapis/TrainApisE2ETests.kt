package com.federicopeyrani.trainapis

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.convention.TestBean
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.test.web.reactive.server.expectBodyList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import com.federicopeyrani.trainapis.model.StationDetails as StationDetailsDto
import com.federicopeyrani.trainapis.model.Trip as TripDto
import com.federicopeyrani.trainapis.model.TripSearch

/**
 * End-to-end: the whole application over HTTP, with only the outbound HTTP engine (recorded ViaggiaTreno responses)
 * and the clock replaced. "Now" is 2026-01-23 22:20 Europe/Rome, the time of the recorded Milano Centrale board.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class TrainApisE2ETests(
    @Autowired private val webTestClient: WebTestClient,
    @Autowired private val upstream: MockEngine,
) {

    companion object {

        @JvmStatic
        fun clock(): Clock = object : Clock {
            override fun now() = Instant.parse("2026-01-23T21:20:00Z")
        }
    }

    @TestBean
    private lateinit var clock: Clock

    @TestConfiguration
    class Upstream {

        /**
         * Recorded responses, plus the upstream answers that cannot be recorded as files.
         */
        @Bean
        fun viaggiaTreno(): HttpClientEngine = mockEngine(
            statuses = mapOf(
                // Unknown station (its details are a recorded placeholder)
                "regione/S99999" to HttpStatusCode.NoContent,
                // Unknown train
                "andamentoTreno/S01700/1/1769122800000" to HttpStatusCode.NoContent,
                // Outage while fetching a train of the S01700 board (the one to Lecco); its destination cannot be
                // resolved by name either, as there is no `cercaStazione/LECCO` fixture
                "andamentoTreno/S01700/24886/1769122800000" to HttpStatusCode.InternalServerError,
                // Rate limited board
                "partenze/S01645/Fri Jan 23 2026 22:20:00 GMT+0100" to HttpStatusCode.Forbidden,
                // Rate limited station lookup, during a search
                "regione/S00450" to HttpStatusCode.Forbidden,
            )
        )
    }

    /**
     * Runs [block] and returns the upstream requests it caused.
     */
    private fun upstreamRequestsDuring(block: () -> Unit): List<String> {
        val before = upstream.requestHistory.size
        block()
        return upstream.requestHistory.drop(before).map { it.upstreamPath() }
    }

    private fun getTrips(uri: String) = webTestClient.get().uri(uri)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk
        .expectBodyList<TripDto>()
        .returnResult()
        .responseBody!!

    private fun searchTrips(uri: String) = webTestClient.get().uri(uri)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk
        .expectBody<TripSearch>()
        .returnResult()
        .responseBody!!

    @Test
    fun `departures default to now, carry each train's run, and leave out trains going nowhere known`() {
        lateinit var departures: List<TripDto>
        val requests = upstreamRequestsDuring { departures = getTrips("/departures/S01700") }

        assertEquals(7, departures.size, "the train to Lecco has neither run nor resolvable destination")
        assertTrue(departures.all { it.from.station.id == "S01700" && it.run != null })
        val toLingotto = departures.single { it.run?.train?.id == "7644" }
        assertEquals("Torino Lingotto", toLingotto.to.station.name)
        assertEquals("2026-01-23T21:15:00Z", toLingotto.from.departureTime?.scheduled.toString())

        assertEquals(requests.distinct(), requests, "each upstream resource is fetched once per request")
    }

    @Test
    fun `trips only include trains calling at the destination`() {
        val search = searchTrips("/trips/S01700/S00452?date=2026-01-23T21:20:00")

        assertEquals(1, search.unverifiedDepartures, "the train to Lecco could not be checked")
        assertTrue(search.trips.isNotEmpty())
        search.trips.forEach { trip ->
            assertEquals("S00452", trip.to.station.id)
            val calls = listOf(trip.run!!.origin.station.id) + trip.run.stops.map { it.station.id } +
                trip.run.destination.station.id
            assertTrue(calls.indexOf("S01700") < calls.indexOf("S00452"), "${trip.run.id} calls at S00452 after S01700")
        }
    }

    @Test
    fun `all trips match every destination against each origin board once`() {
        lateinit var search: TripSearch
        val requests = upstreamRequestsDuring {
            search = searchTrips("/trips/all?from=S01700&from=S01700&to=S00452&to=S01915")
        }

        assertEquals(setOf("S00452", "S01915"), search.trips.map { it.to.station.id }.toSet())
        assertEquals(1, search.unverifiedDepartures, "the train to Lecco, counted once for both destinations")
        assertTrue(requests.count { it.startsWith("partenze/") } <= 1)
    }

    @Test
    fun `run is assembled from the train status and its stations`() {
        webTestClient.get().uri("/runs/S01700_2185_1769122800000")
            .exchange()
            .expectStatus().isOk
            .expectBody().json(
                javaClass.getResource("/expected/e2e/run-S01700_2185_1769122800000.json")!!.readText(),
                JsonCompareMode.STRICT,
            )
    }

    @Test
    fun `station search skips stations whose details cannot be fetched, and ignores blank queries`() {
        val stations = webTestClient.get().uri("/stations?query=torino")
            .exchange()
            .expectStatus().isOk
            .expectBodyList<StationDetailsDto>()
            .returnResult()
            .responseBody!!

        assertEquals(listOf("S00035", "S00452"), stations.map { it.id }.sorted())

        val requests = upstreamRequestsDuring {
            webTestClient.get().uri("/stations?query= ")
                .exchange()
                .expectStatus().isOk
                .expectBody().json("[]")
        }
        assertEquals(emptyList(), requests)
    }

    @ParameterizedTest
    @CsvSource(
        "/departures/S99999, 404",
        "/trips/S99999/S00452, 404",
        "/runs/S01700_1_1769122800000, 404",
        "/runs/not-a-run-id, 400",
        "/departures/S01645, 503",
    )
    fun `failures become problem responses`(uri: String, status: Int) {
        webTestClient.get().uri(uri)
            .exchange()
            .expectStatus().isEqualTo(status)
            .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody().jsonPath("$.status").isEqualTo(status)
    }
}
