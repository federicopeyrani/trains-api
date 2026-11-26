package com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno

import com.federicopeyrani.trainapis.ProviderContractTest
import com.federicopeyrani.trainapis.shared.domain.ProviderUnavailableException
import com.federicopeyrani.trainapis.shared.infrastructure.cache.Cache
import com.federicopeyrani.trainapis.trip.adapter.outbound.viaggiatreno.ViaggiaTrenoTripMapper.toRun
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import org.junit.jupiter.api.Assumptions.abort
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Checks, against the real ViaggiaTreno API, what the adapters rely on: payloads decode with `viaggiatreno-api.yaml`,
 * "unknown" is answered with `204 No Content`, and live data maps into domain models.
 *
 * About 7 upstream calls. When ViaggiaTreno rate limits this client (403), the tests are aborted (skipped) rather
 * than failed, since that says nothing about the contract.
 */
@ProviderContractTest
class ViaggiaTrenoContractTests {

    private val rome = TimeZone.of("Europe/Rome")
    private val cache = Cache(Clock.System)
    private val stationsApi = ViaggiaTrenoClients.stationsApi()
    private val trainsApi = ViaggiaTrenoClients.trainsApi()
    // No station lists here (they are 23 calls): the direct lookups are what these tests check
    private val stations = ViaggiaTrenoStationsApiWrapper(stationsApi, ViaggiaTrenoStationRegistry(stationsApi, cache, emptyList()), cache)
    private val trains = ViaggiaTrenoTrainsApiWrapper(trainsApi, cache)

    private suspend fun <T> upstream(block: suspend () -> T): T =
        try {
            block()
        } catch (e: ProviderUnavailableException) {
            if (e.message.orEmpty().contains("HTTP 403")) abort<Nothing>("ViaggiaTreno is rate limiting: ${e.message}")
            throw e
        }

    private fun assumeNotRateLimited(status: Int) {
        if (status == 403) abort<Nothing>("ViaggiaTreno is rate limiting this client")
    }

    @Test
    fun `station search finds stations by name`() = runTest(timeout = 60.seconds) {
        val results = upstream { stations.searchStations("MILANO CENTRALE").data }

        assertTrue(results.any { it.id == "S01700" && it.nomeLungo == "MILANO CENTRALE" }, "$results")
    }

    @Test
    fun `station details carry the name and coordinates`() = runTest(timeout = 60.seconds) {
        val details = assertNotNull(upstream { stations.getStationDetails("S01700") })

        assertEquals("S01700", details.codStazione)
        assertEquals("MILANO CENTRALE", details.localita?.nomeLungo)
        assertTrue(abs(details.lat!! - 45.486) < 0.05 && abs(details.lon!! - 9.204) < 0.05, "${details.lat}, ${details.lon}")
    }

    @Test
    fun `a region's station list carries names and coordinates`() = runTest(timeout = 60.seconds) {
        // One list (Lombardia) of the 23 the registry loads
        val lombardia = ViaggiaTrenoStationRegistry(stationsApi, cache, regions = listOf(1))

        val centrale = assertNotNull(upstream { lombardia.find("S01700") }, "Milano Centrale is listed in Lombardia")
        assertEquals("MILANO CENTRALE", centrale.localita?.nomeLungo)
        assertTrue(abs(centrale.lat!! - 45.486) < 0.05 && abs(centrale.lon!! - 9.204) < 0.05, "${centrale.lat}, ${centrale.lon}")
    }

    @Test
    fun `unknown stations and trains are answered with no content`() = runTest(timeout = 60.seconds) {
        val region = stationsApi.getRegione("S99999")
        assumeNotRateLimited(region.status)
        assertEquals(204, region.status, "region of an unknown station")
        // `/departures/{from}` answers 404 only if the details of an unknown station are absent
        assertNull(upstream { stations.getStationDetails("S99999") }, "details of an unknown station")

        val today = Clock.System.now().toLocalDateTime(rome).date.atStartOfDayIn(rome).toEpochMilliseconds()
        val train = trainsApi.andamentoTreno("S01700", 1, today)
        assumeNotRateLimited(train.status)
        assertEquals(204, train.status, "status of an unknown train")
    }

    @Test
    fun `departure board trains can be followed and mapped into runs`() = runTest(timeout = 60.seconds) {
        val now = Clock.System.now()
        val board = upstream { trains.getDepartures("S01700", now).data }
        assumeTrue(board.isNotEmpty(), "Milano Centrale has no departures at $now")

        // The adapter takes the departure time from `orarioPartenza`: it must be this station's departure
        board.forEach {
            val local = Instant.fromEpochMilliseconds(it.orarioPartenza).toLocalDateTime(rome).time
            assertEquals(it.compOrarioPartenza, local.format(LocalTime.Formats.ISO).take(5), "train ${it.numeroTreno}")
        }

        val entry = board.first()
        val train = assertNotNull(upstream { trains.getTrain(entry).data }, "status of train ${entry.numeroTreno}")
        assertEquals(entry.numeroTreno, train.numeroTreno)
        assertNotNull(train.arrivato, "tracking relies on `arrivato`")

        val run = train.toRun(entry.dataPartenzaTreno, now, stations = emptyMap())
        val calls = listOf(run.origin.station.id.value) + run.stops.map { it.station.id.value } + run.destination.station.id.value
        assertEquals(entry.codOrigine, run.origin.station.id.value)
        assertTrue("S01700" in calls, "run ${run.id} calls at $calls")
        val scheduled = listOfNotNull(run.origin.departureTime?.scheduled) +
            run.stops.mapNotNull { it.departureTime?.scheduled ?: it.arrivalTime?.scheduled } +
            listOfNotNull(run.destination.arrivalTime?.scheduled)
        assertEquals(scheduled.sorted(), scheduled, "calls of ${run.id} are in time order")
    }
}
