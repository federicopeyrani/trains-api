package com.federicopeyrani.trainapis.station.adapter.outbound.viaggiatreno

import com.federicopeyrani.trainapis.shared.domain.StationId
import com.federicopeyrani.trainapis.shared.domain.Station
import com.federicopeyrani.trainapis.mockEngine
import com.federicopeyrani.trainapis.shared.infrastructure.cache.Cache
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoClients
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoProperties
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoStationRegistry
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoStationsApiWrapper
import com.federicopeyrani.trainapis.station.domain.GeoLocation
import com.federicopeyrani.trainapis.station.domain.StationDetails
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

/**
 * The ViaggiaTreno stations adapter against recorded upstream responses. The `torino` search lists Torino Porta Susa
 * (S00035), Torino Lingotto (S00452) and Torino Stura (S00450).
 */
class ViaggiaTrenoStationsProviderTests {

    private fun provider(statuses: Map<String, HttpStatusCode> = emptyMap()): ViaggiaTrenoStationsProvider {
        val stationsApi = ViaggiaTrenoClients.stationsApi(mockEngine(statuses = statuses))
        val cache = Cache(Clock.System)
        return ViaggiaTrenoStationsProvider(
            viaggiaTrenoStationsApiWrapper = ViaggiaTrenoStationsApiWrapper(
                stationsApi = stationsApi,
                // No station lists: every station is looked up directly, the path these tests exercise
                registry = ViaggiaTrenoStationRegistry(stationsApi, cache, regions = emptyList()),
                cache = cache,
            ),
            properties = ViaggiaTrenoProperties(),
        )
    }

    @Test
    fun `enriches search results with details and skips stations whose details cannot be fetched`() = runTest {
        val stations = provider(mapOf("regione/S00450" to HttpStatusCode.Forbidden))
            .getStations("torino")
            .toList()

        assertEquals(
            setOf(
                StationDetails(Station(StationId("S00035"), "Torino Porta Susa"), GeoLocation(45.075906, 7.667673)),
                StationDetails(Station(StationId("S00452"), "Torino Lingotto"), GeoLocation(45.0266, 7.657469)),
            ),
            stations.toSet(),
        )
    }

    @Test
    fun `search without matches is empty`() = runTest {
        val stations = provider(mapOf("cercaStazione/nowhere" to HttpStatusCode.NoContent))
            .getStations("nowhere")
            .toList()

        assertEquals(emptyList(), stations)
    }
}
