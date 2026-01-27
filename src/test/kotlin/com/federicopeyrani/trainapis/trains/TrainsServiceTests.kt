package com.federicopeyrani.trainapis.trains

import com.federicopeyrani.trainapis.providers.viaggiatreno.ViaggiaTrenoClient
import com.federicopeyrani.trainapis.providers.viaggiatreno.ViaggiaTrenoConfig
import com.federicopeyrani.trainapis.providers.viaggiatreno.ViaggiaTrenoMockEngineConfiguration
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest(classes = [TripsService::class, ViaggiaTrenoClient::class, ViaggiaTrenoConfig::class, ViaggiaTrenoMockEngineConfiguration::class])
class TrainsServiceTests(
    @Autowired private val trainsService: TripsService,
) {

    @Test
    fun `should fetch departures`() = runTest {
        val date = LocalDateTime(2026, 1, 23, 22, 20, 30)
        val departures = trainsService.getDepartures("S01700", date)

        assertNotNull(departures.firstOrNull()).let {
            it.from.let {
                assertEquals(it.id, "S01700")
                assertEquals(it.name, "MILANO CENTRALE")
            }
            it.to.let {
                assertEquals(it.id, "S00452")
                assertEquals(it.name, "TORINO LINGOTTO")
            }
            it.departureTime.let {
                assertEquals(LocalDateTime(2026, 1, 23, 22, 15), it.scheduled)
                assertEquals(LocalDateTime(2026, 1, 23, 22, 15), it.expected)
                assertEquals(LocalDateTime(2026, 1, 23, 22, 18), it.actual)
            }
            assertNotNull(it.run).let {
                it.originDepartureTime.let {
                    assertEquals(LocalDateTime(2026, 1, 23, 22, 15), it.scheduled)
                }
            }
        }
    }
}
