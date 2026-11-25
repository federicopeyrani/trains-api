package com.federicopeyrani.trainapis.trip.application

import com.federicopeyrani.trainapis.mockEngine
import com.federicopeyrani.trainapis.shared.infrastructure.cache.Cache
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoClients
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoProperties
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoStationsApiWrapper
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoTrainsApiWrapper
import com.federicopeyrani.trainapis.trip.adapter.outbound.viaggiatreno.ViaggiaTrenoTripsProvider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Test
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock

/**
 * Runs the real service and ViaggiaTreno adapter against recorded upstream responses (no Spring context).
 */
class TripServiceTests {

    private val engine = mockEngine("/providers/viaggiatreno")
    private val cache = Cache(ConcurrentMapCacheManager("test"), "test")

    private val tripService = TripService(
        provider = ViaggiaTrenoTripsProvider(
            trainsApiWrapper = ViaggiaTrenoTrainsApiWrapper(ViaggiaTrenoClients.trainsApi(engine), cache),
            stationApiWrapper = ViaggiaTrenoStationsApiWrapper(ViaggiaTrenoClients.stationsApi(engine), cache),
            properties = ViaggiaTrenoProperties(),
        ),
        clock = Clock.System,
    )

    @Test
    fun `should fetch departures`() = runTest {
        val zone = TimeZone.of("Europe/Rome")
        val date = LocalDateTime(2026, 1, 23, 22, 20, 30).toInstant(zone)
        val trips = tripService.getDepartures("S01700", date).toList()
        val trip = trips.first { it.to.station.id == "S00452" }

        trip.let {
            it.from.let {
                it.station.let {
                    assertEquals(it.id, "S01700")
                    assertEquals(it.name, "Milano Centrale")
                }
                it.departureTime.let {
                    assertEquals(LocalDateTime(2026, 1, 23, 21, 15), it?.scheduled)
                    assertEquals(LocalDateTime(2026, 1, 23, 21, 15), it?.expected)
                    assertEquals(LocalDateTime(2026, 1, 23, 21, 18), it?.actual)
                }
            }
            it.to.let {
                it.station.let {
                    assertEquals(it.id, "S00452")
                    assertEquals(it.name, "Torino Lingotto")
                }
            }
            assertNotNull(it.run).let {
                it.origin.departureTime.let {
                    assertEquals(LocalDateTime(2026, 1, 23, 21, 15), it?.scheduled)
                }
            }
        }
    }
}
