package com.federicopeyrani.trainapis.trip.application

import com.federicopeyrani.trainapis.trip.domain.TripFixtures.id
import com.federicopeyrani.trainapis.shared.domain.StationId
import com.federicopeyrani.trainapis.trip.application.port.inbound.TripSearchResult
import com.federicopeyrani.trainapis.trip.application.port.outbound.TripsProvider
import com.federicopeyrani.trainapis.trip.domain.Departure
import com.federicopeyrani.trainapis.trip.domain.Run
import com.federicopeyrani.trainapis.trip.domain.RunId
import com.federicopeyrani.trainapis.trip.domain.TripFixtures.departure
import com.federicopeyrani.trainapis.trip.domain.TripFixtures.run
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Application rules exercised against an in-memory [TripsProvider].
 */
class TripServiceRulesTests {

    private class InMemoryTripsProvider(private val boards: Map<StationId, List<Departure>>) : TripsProvider {

        val boardRequests = mutableListOf<StationId>()

        override fun getDepartures(from: StationId, date: Instant): Flow<Departure> {
            boardRequests += from
            return boards[from].orEmpty().asFlow()
        }

        override suspend fun getRun(runId: RunId): Run = boards.values.flatten()
            .mapNotNull { it.run }
            .first { it.id == runId }
    }

    private val northbound = run("A", "B", "C", trainNumber = 1)
    private val southbound = run("C", "B", "A", trainNumber = 2)
    private val unknownRun = departure("B", run = null)
    private val unknownRunAndDestination = departure("B", run = null, destination = null)

    private val provider = InMemoryTripsProvider(
        mapOf(
            id("B") to listOf(
                departure("B", northbound),
                departure("B", southbound),
                unknownRun,
                unknownRunAndDestination,
            ),
            id("A") to listOf(departure("A", northbound)),
        )
    )

    private val service = TripService(provider, Clock.System)

    private fun List<TripSearchResult>.trips() = filterIsInstance<TripSearchResult.Match>().map { it.trip }
    private fun List<TripSearchResult>.unverified() = filterIsInstance<TripSearchResult.Unverified>().map { it.departure }

    @Test
    fun `trips only include runs calling at the destination after the origin`() = runTest {
        val results = service.getTrips(id("B"), id("C"), date = null).toList()

        assertEquals(listOf("1"), results.trips().map { it.run?.train?.number?.toString() })
        assertEquals("C", results.trips().single().to.station.id.value)
    }

    @Test
    fun `departures whose run is unknown are reported as unverified, not as trips`() = runTest {
        val results = service.getTrips(id("B"), id("C"), date = null).toList()

        assertEquals(listOf(unknownRun, unknownRunAndDestination), results.unverified())
    }

    @Test
    fun `all trips fetch each origin board once and report each unverified departure once`() = runTest {
        val results = service.getAllTrips(listOf("A", "B", "B").map(::id), listOf("A", "C").map(::id), date = null).toList()

        assertEquals(listOf("A", "B"), provider.boardRequests.map { it.value }.sorted())
        assertEquals(
            setOf("A" to "C", "B" to "C", "B" to "A"),
            results.trips().map { it.from.station.id.value to it.to.station.id.value }.toSet(),
        )
        assertEquals(listOf(unknownRun, unknownRunAndDestination), results.unverified(), "once each, although they may reach both destinations")
    }

    @Test
    fun `departures leave out entries whose destination is unknown`() = runTest {
        val departures = service.getDepartures(id("B"), date = null).toList()

        assertEquals(listOf(northbound, southbound, null), departures.map { it.run }, "the last board entry is left out")
    }

    @Test
    fun `run is looked up by its parsed id`() = runTest {
        assertEquals(northbound, service.getRun(northbound.id.toString()))
    }
}
