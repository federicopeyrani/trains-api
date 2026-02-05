package com.federicopeyrani.trainapis.trains

import com.federicopeyrani.trainapis.data.Cache
import com.federicopeyrani.trainapis.providers.viaggiatreno.ViaggiaTrenoConfig
import com.federicopeyrani.trainapis.providers.viaggiatreno.ViaggiaTrenoMockEngineConfiguration
import com.federicopeyrani.trainapis.providers.viaggiatreno.ViaggiaTrenoStationsApiWrapper
import com.federicopeyrani.trainapis.providers.viaggiatreno.ViaggiaTrenoStationsProvider
import com.federicopeyrani.trainapis.providers.viaggiatreno.ViaggiaTrenoTrainsApiWrapper
import com.federicopeyrani.trainapis.providers.viaggiatreno.ViaggiaTrenoTripsProvider
import kotlinx.coroutines.flow.first
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
        TripsService::class,
        ViaggiaTrenoTripsProvider::class,
        ViaggiaTrenoTrainsApiWrapper::class,
        ViaggiaTrenoStationsProvider::class,
        ViaggiaTrenoStationsApiWrapper::class,
        ViaggiaTrenoConfig::class,
        Cache.Factory::class,
        ViaggiaTrenoMockEngineConfiguration::class,
    ]
)
class TrainsServiceTests(
    @Autowired private val trainsService: TripsService,
) {

    @Test
    fun `should fetch departures`() = runTest {
        val zone = TimeZone.of("Europe/Rome")
        val date = LocalDateTime(2026, 1, 23, 22, 20, 30).toInstant(zone)
        val trip = trainsService.getDepartures("S01700", date).first()

        trip.let {
            it.from.let {
                assertEquals(it.id, "S01700")
                assertEquals(it.name, "MILANO CENTRALE")
            }
            it.to.let {
                assertEquals(it.id, "S00452")
                assertEquals(it.name, "TORINO LINGOTTO")
            }
            it.departureTime.let {
                assertEquals(LocalDateTime(2026, 1, 23, 22, 15), it?.scheduled)
                assertEquals(LocalDateTime(2026, 1, 23, 22, 15), it?.expected)
                assertEquals(LocalDateTime(2026, 1, 23, 22, 18), it?.actual)
            }
            assertNotNull(it.run).let {
                it.origin.departureTime.let {
                    assertEquals(LocalDateTime(2026, 1, 23, 22, 15), it.scheduled)
                }
            }
        }
    }
}
