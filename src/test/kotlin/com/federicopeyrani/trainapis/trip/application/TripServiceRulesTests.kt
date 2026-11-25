package com.federicopeyrani.trainapis.trip.application

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

    private class InMemoryTripsProvider(private val boards: Map<String, List<Departure>>) : TripsProvider {

        val boardRequests = mutableListOf<String>()

        override fun getDepartures(from: String, date: Instant): Flow<Departure> {
            boardRequests += from
            return boards[from].orEmpty().asFlow()
        }

        override suspend fun getRun(runId: RunId): Run = boards.values.flatten()
            .mapNotNull { it.run }
            .first { it.id == runId }
    }

    private val northbound = run("A", "B", "C", trainNumber = 1)
    private val southbound = run("C", "B", "A", trainNumber = 2)

    private val provider = InMemoryTripsProvider(
        mapOf(
            "B" to listOf(departure("B", northbound), departure("B", southbound), departure("B", run = null)),
            "A" to listOf(departure("A", northbound)),
        )
    )

    private val service = TripService(provider, Clock.System)

    @Test
    fun `trips only include runs calling at the destination after the origin`() = runTest {
        val trips = service.getTrips("B", "C", date = null).toList()

        assertEquals(listOf("1"), trips.map { it.run?.train?.id })
        assertEquals("C", trips.single().to.station.id)
    }

    @Test
    fun `all trips fetch each origin board once`() = runTest {
        val trips = service.getAllTrips(listOf("A", "B", "B"), listOf("A", "C"), date = null).toList()

        assertEquals(listOf("A", "B"), provider.boardRequests.sorted())
        assertEquals(
            setOf("A" to "C", "B" to "C", "B" to "A"),
            trips.map { it.from.station.id to it.to.station.id }.toSet(),
        )
    }

    @Test
    fun `run is looked up by its parsed id`() = runTest {
        assertEquals(northbound, service.getRun(northbound.id.toString()))
    }
}
