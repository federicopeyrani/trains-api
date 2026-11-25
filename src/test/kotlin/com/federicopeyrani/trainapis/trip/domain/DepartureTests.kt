package com.federicopeyrani.trainapis.trip.domain

import com.federicopeyrani.trainapis.trip.domain.TripFixtures.departure
import com.federicopeyrani.trainapis.trip.domain.TripFixtures.run
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
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

        assertTrue(fromB.serves("C"))
        assertTrue(fromB.serves("D"))
        assertFalse(fromB.serves("A"), "A is called at before B")
        assertFalse(fromB.serves("Z"), "Z is not called at")
        assertFalse(departure("B", run = null).serves("C"), "unknown runs never qualify")
    }

    @Test
    fun `trip to an intermediate stop uses that stop's arrival`() {
        val trip = departure("A", run).toTrip(to = "C")

        assertEquals("C", trip.to.station.id)
        assertEquals(LocalDateTime(2026, 1, 23, 10, 0), trip.to.arrivalTime?.scheduled)
        assertEquals(StopStatus.NOT_YET_ARRIVED, trip.to.status)
    }

    @Test
    fun `trip without destination ends at the run's destination`() {
        val trip = departure("B", run).toTrip()

        assertEquals("B", trip.from.station.id)
        assertEquals(LocalDateTime(2026, 1, 23, 9, 0), trip.from.departureTime?.scheduled)
        assertEquals(run.destination, trip.to)
    }

    @Test
    fun `trip without run falls back to the board`() {
        val trip = departure("A", run = null).toTrip()

        assertEquals("board-destination", trip.to.station.id)
        assertNull(trip.from.status)
        assertNull(trip.to.arrivalTime)
    }

    @Test
    fun `run id round-trips and rejects malformed values`() {
        assertEquals(run.id, RunId.parse(run.id.toString()))
        listOf("", "A_1", "A_x_1", "A_1_x", "_1_2", "A_1_2_3").forEach {
            assertFailsWith<InvalidRunIdException>(it) { RunId.parse(it) }
        }
    }
}
