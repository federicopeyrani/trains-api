package com.federicopeyrani.trainapis.trip.adapter.outbound.viaggiatreno

import com.federicopeyrani.trainapis.shared.domain.StationId
import com.federicopeyrani.trainapis.shared.domain.Station
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.model.AndamentoTreno
import com.federicopeyrani.trainapis.trip.adapter.outbound.viaggiatreno.ViaggiaTrenoTripMapper.stationIds
import com.federicopeyrani.trainapis.trip.adapter.outbound.viaggiatreno.ViaggiaTrenoTripMapper.toRun
import com.federicopeyrani.trainapis.trip.domain.RunStatus
import com.federicopeyrani.trainapis.trip.domain.StopStatus
import kotlinx.serialization.json.Json
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Maps recorded ViaggiaTreno payloads straight into domain models: no HTTP, no Spring.
 */
class ViaggiaTrenoTripMapperTests {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val train = json.decodeFromString<AndamentoTreno>(
        javaClass
            .getResource("/providers/viaggiatreno/GET_infomobilita_resteasy_viaggiatreno_andamentoTreno_S01700_2185_1769122800000.json")!!
            .readText()
    )

    private val stations = train.stationIds().associateWith { Station(id = it, name = "Station $it") }

    @Test
    fun `maps a train into a run`() {
        val run = train.toRun(departureDate = 1769122800000, lastUpdate = Instant.fromEpochSeconds(0), stations)

        assertEquals("S01700_2185_1769122800000", run.id.toString())
        assertEquals("S01700", run.origin.station.id.value)
        assertEquals("S01915", run.destination.station.id.value)
        assertEquals(9, run.stops.size)
        assertEquals(RunStatus.DELAYED, run.status, "5 minutes late")
        assertEquals(StopStatus.DEPARTED, run.origin.status)
        assertEquals(StopStatus.NOT_YET_ARRIVED, run.destination.status)
    }

    @ParameterizedTest
    @CsvSource(
        "PG, 0,     , ON_TIME",
        "PG, 0,    0, ON_TIME",
        "PG, 0,   -3, ON_TIME",
        "PG, 0,    1, DELAYED",
        "ST, 1,     , CANCELLED",
        "PG, 2,    0, ON_TIME",
    )
    fun `derives the run status from cancellation and delay`(type: String, measure: Int, delay: Int?, status: RunStatus) {
        val run = train.copy(tipoTreno = AndamentoTreno.TipoTreno.valueOf(type), provvedimento = measure, ritardo = delay)
            .toRun(departureDate = 1769122800000, lastUpdate = Instant.fromEpochSeconds(0), stations)

        assertEquals(status, run.status)
    }

    @Test
    fun `falls back to the stop name when a station was not resolved`() {
        val run = train.toRun(departureDate = 1769122800000, lastUpdate = Instant.fromEpochSeconds(0), stations - StationId("S01915"))

        assertEquals("Cremona", run.destination.station.name)
    }

    /**
     * The train left at 22:20 Rome (21:20 UTC) and runs 5 minutes late (`ritardo`): events that happened have their
     * actual time, and those still to come are forecast as scheduled + 5 minutes, at every stop alike.
     */
    @Test
    fun `forecasts events still to come with the train's delay`() {
        val run = train.toRun(departureDate = 1769122800000, lastUpdate = Instant.fromEpochSeconds(0), stations)

        val departure = run.origin.departureTime!!
        assertEquals(Instant.parse("2026-01-23T21:20:00Z"), departure.scheduled)
        assertNull(departure.expected, "already departed")

        val next = run.stops.first().arrivalTime!!
        assertNull(next.actual)
        assertEquals(next.scheduled + 5.minutes, next.expected)

        val arrival = run.destination.arrivalTime!!
        assertEquals(Instant.parse("2026-01-23T22:35:00Z"), arrival.scheduled)
        assertEquals(Instant.parse("2026-01-23T22:40:00Z"), arrival.expected)
    }

    @Test
    fun `forecasts nothing before the train has a delay`() {
        val run = train.copy(ritardo = null)
            .toRun(departureDate = 1769122800000, lastUpdate = Instant.fromEpochSeconds(0), stations)

        assertNull(run.destination.arrivalTime?.expected)
    }
}
