package com.federicopeyrani.trainapis.trip.domain

import com.federicopeyrani.trainapis.trip.domain.TripFixtures.id
import com.federicopeyrani.trainapis.trip.domain.TripFixtures.departure
import com.federicopeyrani.trainapis.trip.domain.TripFixtures.run
import kotlin.test.Test
import kotlin.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DepartureTests {

    private val run = run("A", "B", "C", "D")

    @Test
    fun `serves stations called at after the departure station`() {
        val fromB = departure("B", run)

        assertTrue(fromB.serves(id("C")))
        assertTrue(fromB.serves(id("D")))
        assertFalse(fromB.serves(id("A")), "A is called at before B")
        assertFalse(fromB.serves(id("Z")), "Z is not called at")
        assertFalse(departure("B", run = null).serves(id("C")), "unknown runs never qualify")
    }

    @Test
    fun `trip to an intermediate stop uses that stop's arrival`() {
        val trip = departure("A", run).toTrip(to = id("C"))

        assertEquals("C", trip.to.station.id.value)
        assertEquals(Instant.parse("2026-01-23T10:00:00Z"), trip.to.arrivalTime?.scheduled)
        assertEquals(StopStatus.NOT_YET_ARRIVED, trip.to.status)
    }

    @Test
    fun `trip without destination ends at the run's destination`() {
        val trip = departure("B", run).toTrip()

        assertEquals("B", trip.from.station.id.value)
        assertEquals(Instant.parse("2026-01-23T09:00:00Z"), trip.from.departureTime?.scheduled)
        assertEquals(run.destination, trip.to)
    }

    @Test
    fun `trip without run falls back to the board`() {
        val trip = departure("A", run = null).toTrip()

        assertEquals("board-destination", trip.to.station.id.value)
        assertNull(trip.from.status)
        assertNull(trip.to.arrivalTime)
    }

    @Test
    fun `departure from a run's last call falls back to the board`() {
        val trip = departure("D", run).toTrip()

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
