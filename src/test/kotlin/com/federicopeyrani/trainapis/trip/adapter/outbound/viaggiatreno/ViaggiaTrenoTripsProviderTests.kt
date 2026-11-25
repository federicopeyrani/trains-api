package com.federicopeyrani.trainapis.trip.adapter.outbound.viaggiatreno

import com.federicopeyrani.trainapis.trip.domain.TrainNumber
import com.federicopeyrani.trainapis.shared.domain.StationId
import com.federicopeyrani.trainapis.mockEngine
import com.federicopeyrani.trainapis.shared.domain.ProviderUnavailableException
import com.federicopeyrani.trainapis.shared.domain.Station
import com.federicopeyrani.trainapis.shared.domain.StationNotFoundException
import com.federicopeyrani.trainapis.shared.infrastructure.cache.Cache
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoClients
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoProperties
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoStationsApiWrapper
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoTrainsApiWrapper
import com.federicopeyrani.trainapis.trip.domain.RunId
import com.federicopeyrani.trainapis.trip.domain.RunNotFoundException
import com.federicopeyrani.trainapis.upstreamPath
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The ViaggiaTreno trips adapter against recorded upstream responses: mapping, best-effort degradation and upstream
 * traffic. The board fixture is Milano Centrale (S01700) at 22:20 Europe/Rome on 2026-01-23, listing 8 trains.
 */
class ViaggiaTrenoTripsProviderTests {

    private val boardTime = Instant.parse("2026-01-23T21:20:00Z")
    private val boardPath = "partenze/S01700/Fri Jan 23 2026 22:20:00 GMT+0100"
    private fun trainPath(number: Int) = "andamentoTreno/S01700/$number/1769122800000"

    private class Upstream(statuses: Map<String, HttpStatusCode> = emptyMap()) {

        val engine = mockEngine(statuses = statuses)
        private val cache = Cache(Clock.System)

        val provider = ViaggiaTrenoTripsProvider(
            trainsApiWrapper = ViaggiaTrenoTrainsApiWrapper(ViaggiaTrenoClients.trainsApi(engine), cache),
            stationApiWrapper = ViaggiaTrenoStationsApiWrapper(ViaggiaTrenoClients.stationsApi(engine), cache),
            properties = ViaggiaTrenoProperties(),
        )

        fun requests() = engine.requestHistory.map { it.upstreamPath() }
    }

    @Test
    fun `maps every board entry with its run, fetching each upstream resource once`() = runTest {
        val upstream = Upstream()

        val departures = upstream.provider.getDepartures(StationId("S01700"), boardTime).toList()

        assertEquals(8, departures.size)
        val toLingotto = departures.single { it.run?.train?.number == TrainNumber(7644) }
        assertEquals(Station(StationId("S01700"), "Milano Centrale"), toLingotto.station)
        assertEquals(Station(StationId("S00452"), "Torino Lingotto"), toLingotto.destination)
        assertEquals(Instant.parse("2026-01-23T21:15:00Z"), toLingotto.scheduledDeparture)
        assertEquals(Instant.parse("2026-01-23T21:18:00Z"), toLingotto.run?.origin?.departureTime?.actual)

        val duplicated = upstream.requests().groupingBy { it }.eachCount().filterValues { it > 1 }
        assertEquals(emptyMap(), duplicated, "stations shared by several trains must be fetched once")
    }

    @Test
    fun `keeps a departure without its run when the train cannot be fetched`() = runTest {
        val upstream = Upstream(mapOf(trainPath(7644) to HttpStatusCode.InternalServerError))

        val departures = upstream.provider.getDepartures(StationId("S01700"), boardTime).toList()

        assertEquals(8, departures.size)
        val withoutRun = departures.single { it.run == null }
        assertEquals(Station(StationId("S00452"), "Torino Lingotto"), withoutRun.destination, "resolved from the board's name")
        assertEquals(Instant.parse("2026-01-23T21:15:00Z"), withoutRun.scheduledDeparture)
    }

    @Test
    fun `keeps an entry whose train and destination are both unknown, without either`() = runTest {
        val upstream = Upstream(
            mapOf(
                trainPath(2185) to HttpStatusCode.NoContent,
                "cercaStazione/CREMONA" to HttpStatusCode.NoContent,
            )
        )

        val departures = upstream.provider.getDepartures(StationId("S01700"), boardTime).toList()

        assertEquals(8, departures.size)
        val unresolved = departures.single { it.destination == null }
        assertEquals(null, unresolved.run, "callers can still tell the train exists, e.g. to report it as unverified")
    }

    @Test
    fun `fails before fetching the board when the station is unknown`() = runTest {
        val upstream = Upstream(
            mapOf(
                "regione/S99999" to HttpStatusCode.NoContent,
            )
        )

        assertFailsWith<StationNotFoundException> { upstream.provider.getDepartures(StationId("S99999"), boardTime).toList() }
        assertTrue(upstream.requests().none { it.startsWith("partenze/") })
    }

    @Test
    fun `reports the provider as unavailable when the board is rate limited`() = runTest {
        val upstream = Upstream(mapOf(boardPath to HttpStatusCode.Forbidden))

        assertFailsWith<ProviderUnavailableException> { upstream.provider.getDepartures(StationId("S01700"), boardTime).toList() }
    }

    @Test
    fun `requests boards per minute in provider time and reuses them within that minute`() = runTest {
        val upstream = Upstream()

        listOf("2026-01-23T21:20:05Z", "2026-01-23T21:20:59Z").forEach {
            upstream.provider.getDepartures(StationId("S01700"), Instant.parse(it)).toList()
        }

        assertEquals(listOf(boardPath), upstream.requests().filter { it.startsWith("partenze/") })
    }

    @Test
    fun `requests boards with the summer time offset of Europe-Rome`() = runTest {
        val summerBoardPath = "partenze/S01700/Thu Jul 23 2026 22:20:00 GMT+0200"
        val upstream = Upstream(mapOf(summerBoardPath to HttpStatusCode.Forbidden))

        assertFailsWith<ProviderUnavailableException> {
            upstream.provider.getDepartures(StationId("S01700"), Instant.parse("2026-07-23T20:20:00Z")).toList()
        }
        assertEquals(listOf(summerBoardPath), upstream.requests().filter { it.startsWith("partenze/") })
    }

    @Test
    fun `run unknown to the provider is not found`() = runTest {
        val upstream = Upstream(mapOf(trainPath(1) to HttpStatusCode.NoContent))

        assertFailsWith<RunNotFoundException> { upstream.provider.getRun(RunId(StationId("S01700"), TrainNumber(1), 1769122800000)) }
    }
}
