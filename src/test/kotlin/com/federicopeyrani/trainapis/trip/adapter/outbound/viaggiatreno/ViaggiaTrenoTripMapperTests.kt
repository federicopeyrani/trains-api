package com.federicopeyrani.trainapis.trip.adapter.outbound.viaggiatreno

import com.federicopeyrani.trainapis.shared.domain.Station
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.model.AndamentoTreno
import com.federicopeyrani.trainapis.trip.adapter.outbound.viaggiatreno.ViaggiaTrenoTripMapper.stationIds
import com.federicopeyrani.trainapis.trip.adapter.outbound.viaggiatreno.ViaggiaTrenoTripMapper.toRun
import com.federicopeyrani.trainapis.trip.domain.RunStatus
import com.federicopeyrani.trainapis.trip.domain.StopStatus
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        assertEquals("S01700", run.origin.station.id)
        assertEquals("S01915", run.destination.station.id)
        assertEquals(9, run.stops.size)
        assertEquals(RunStatus.ON_TIME, run.status)
        assertEquals(StopStatus.DEPARTED, run.origin.status)
        assertEquals(StopStatus.NOT_YET_ARRIVED, run.destination.status)
    }

    @Test
    fun `fails when a station was not resolved`() {
        assertFailsWith<IllegalStateException> {
            train.toRun(departureDate = 1769122800000, lastUpdate = Instant.fromEpochSeconds(0), stations - "S01915")
        }
    }
}
