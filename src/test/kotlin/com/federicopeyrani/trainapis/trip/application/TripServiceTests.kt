package com.federicopeyrani.trainapis.trip.application

import com.federicopeyrani.trainapis.shared.infrastructure.cache.Cache
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoConfig
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoMockEngineConfiguration
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoStationsApiWrapper
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoTrainsApiWrapper
import com.federicopeyrani.trainapis.trip.adapter.outbound.viaggiatreno.ViaggiaTrenoTripsProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest(
    classes = [
        TripService::class,
        ViaggiaTrenoTripsProvider::class,
        ViaggiaTrenoTrainsApiWrapper::class,
        ViaggiaTrenoStationsApiWrapper::class,
        ViaggiaTrenoConfig::class,
        Cache.Factory::class,
        ViaggiaTrenoMockEngineConfiguration::class,
    ]
)
class TripServiceTests(
    @Autowired private val tripService: TripService,
) {

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
