package com.federicopeyrani.trainapis.trip.adapter.outbound.viaggiatreno

import com.federicopeyrani.trainapis.shared.domain.StationId
import com.federicopeyrani.trainapis.shared.domain.Station
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.model.AndamentoTreno
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.model.PartenzeArrivi
import com.federicopeyrani.trainapis.trip.adapter.outbound.viaggiatreno.ViaggiaTrenoTripMapper.stationIds
import com.federicopeyrani.trainapis.trip.adapter.outbound.viaggiatreno.ViaggiaTrenoTripMapper.toDeparture
import com.federicopeyrani.trainapis.trip.adapter.outbound.viaggiatreno.ViaggiaTrenoTripMapper.toRun
import com.federicopeyrani.trainapis.trip.domain.RunStatus
import com.federicopeyrani.trainapis.trip.domain.StopStatus
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
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

    private val board = json.decodeFromString<List<PartenzeArrivi>>(
        javaClass
            .getResource("/providers/viaggiatreno/GET_infomobilita_resteasy_viaggiatreno_partenze_S01700_Fri-Jan-23-2026-22-20-00-GMT-0100.json")!!
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
        assertEquals(RunStatus.ON_TIME, run.status)
        assertEquals(StopStatus.DEPARTED, run.origin.status)
        assertEquals(StopStatus.NOT_YET_ARRIVED, run.destination.status)
    }

    @Test
    fun `falls back to the stop name when a station was not resolved`() {
        val run = train.toRun(departureDate = 1769122800000, lastUpdate = Instant.fromEpochSeconds(0), stations - StationId("S01915"))

        assertEquals("Cremona", run.destination.station.name)
    }

    /**
     * ViaggiaTreno gives some times only as `HH:mm` in Europe/Rome; after midnight they belong to the next day, not to
     * the day the train left its origin.
     */
    @Test
    fun `times after midnight belong to the following day`() {
        // Left its origin on Jan 23, arrives on Jan 24 at 00:35 Rome (23:35 UTC) and is expected 5 minutes late
        val overnight = train.copy(
            orarioArrivo = Instant.parse("2026-01-23T23:35:00Z").toEpochMilliseconds(),
            compOrarioArrivoZeroEffettivo = "00:40",
        )
        val run = overnight.toRun(departureDate = 1769122800000, lastUpdate = Instant.fromEpochSeconds(0), stations)

        assertEquals(Instant.parse("2026-01-23T23:40:00Z"), run.destination.arrivalTime?.expected)

        // Board entry of the same day's run, leaving this station on Jan 24 at 00:10 Rome (23:10 UTC)
        val entry = board.first().copy(
            dataPartenzaTreno = 1769122800000,
            orarioPartenza = Instant.parse("2026-01-23T23:10:00Z").toEpochMilliseconds(),
            compOrarioPartenza = "00:10",
        )
        val departure = entry.toDeparture(Station(StationId("S01700"), "Milano Centrale"), run.destination.station, Instant.fromEpochSeconds(0), run = null)

        assertEquals(Instant.parse("2026-01-23T23:10:00Z"), departure.scheduledDeparture)
    }
}
