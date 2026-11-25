package com.federicopeyrani.trainapis.trip.domain

import com.federicopeyrani.trainapis.trip.domain.TripFixtures.id
import com.federicopeyrani.trainapis.trip.domain.TripFixtures.departure
import com.federicopeyrani.trainapis.trip.domain.TripFixtures.run
import kotlin.test.Test
import kotlin.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DepartureTests {

    private val run = run("A", "B", "C", "D")

    @Test
    fun `serves stations called at after the departure station`() {
        val fromB = departure("B", run)

        assertEquals(Serves.YES, fromB.serves(id("C")))
        assertEquals(Serves.YES, fromB.serves(id("D")))
        assertEquals(Serves.NO, fromB.serves(id("A")), "A is called at before B")
        assertEquals(Serves.NO, fromB.serves(id("Z")), "Z is not called at")
        assertEquals(Serves.UNKNOWN, departure("B", run = null).serves(id("C")), "without the run, calls are unknown")
    }

    @Test
    fun `cancelled stops are not served`() {
        val partiallyCancelled = run.copy(
            stops = run.stops.map { if (it.station.id == id("C")) it.copy(status = StopStatus.CANCELLED) else it },
        )

        assertEquals(Serves.NO, departure("B", partiallyCancelled).serves(id("C")), "the train skips C")
        assertEquals(Serves.YES, departure("B", partiallyCancelled).serves(id("D")))
        assertEquals(Serves.NO, departure("C", partiallyCancelled).serves(id("D")), "nobody boards at C")
    }

    @Test
    fun `trip to an intermediate stop uses that stop's arrival`() {
        val trip = departure("A", run).toTrip(id("C"))

        assertEquals("C", trip.to.station.id.value)
        assertEquals(Instant.parse("2026-01-23T10:00:00Z"), trip.to.arrivalTime?.scheduled)
        assertEquals(StopStatus.NOT_YET_ARRIVED, trip.to.status)
    }

    @Test
    fun `trip without destination ends at the run's destination`() {
        val trip = assertNotNull(departure("B", run).toTrip())

        assertEquals("B", trip.from.station.id.value)
        assertEquals(Instant.parse("2026-01-23T09:00:00Z"), trip.from.departureTime?.scheduled)
        assertEquals(run.destination, trip.to)
    }

    @Test
    fun `trip without run falls back to the board`() {
        val trip = assertNotNull(departure("A", run = null).toTrip())

        assertEquals("board-destination", trip.to.station.id.value)
        assertNull(trip.from.status)
        assertNull(trip.to.arrivalTime)
        assertNull(departure("A", run = null, destination = null).toTrip(), "nowhere to go without a destination")
    }

    @Test
    fun `trip to a station is only built for a departure that serves it`() {
        assertFailsWith<IllegalStateException> { departure("B", run).toTrip(id("A")) }
        assertFailsWith<IllegalStateException> { departure("B", run = null).toTrip(id("C")) }
    }

    @Test
    fun `departure from a run's last call falls back to the board`() {
        val trip = assertNotNull(departure("D", run).toTrip())

        assertEquals(Instant.parse("2026-01-23T08:00:00Z"), trip.from.departureTime?.scheduled, "board time")
        assertNull(trip.from.status, "a run never departs from its destination")
    }

    @Test
    fun `run id round-trips and rejects malformed values`() {
        assertEquals(run.id, RunId.parse(run.id.toString()))
        listOf("", "A_1", "A_x_1", "A_1_x", "_1_2", "A_1_2_3").forEach {
            assertFailsWith<InvalidRunIdException>(it) { RunId.parse(it) }
        }
    }
}
