package com.federicopeyrani.trainapis.providers.viaggiatreno

import com.federicopeyrani.trainapis.data.Cache
import com.federicopeyrani.trainapis.providers.viaggiatreno.client.api.StationsApi
import com.federicopeyrani.trainapis.utils.log
import org.springframework.stereotype.Component
import kotlin.time.Duration.Companion.minutes

@Component
class ViaggiaTrenoStationsApiWrapper(
    private val stationsApi: StationsApi,
    private val cache: Cache,
) {

    companion object {

        private val log = log()
    }

    suspend fun searchStations(query: String) = cache.getOrPut(
        key = Cache.Key.of("search-stations-$query"),
        ttl = 60.minutes
    ) {
        stationsApi.cercaStazione(query).body()
    }

    suspend fun getStationDetails(stationId: String) = cache.getOrPut(
        key = Cache.Key.of("station-details-$stationId"),
        ttl = 60.minutes
    ) {
        val regionCode = getRegionCode(stationId).data ?: return@getOrPut null

        stationsApi.elencoStazioni(regionCode)
            .body()
            // Not all stations are returned by `elencoStazioni`, even though `getRegione` returns
            // a valid region code. So we need to handle the case where the station is not found.
            .firstOrNull { it.localita.id == stationId }
    }

    private suspend fun getRegionCode(stationId: String) = cache.getOrPut(
        key = Cache.Key.of("station-region-$stationId"),
        ttl = 60.minutes
    ) {
        runCatching { stationsApi.getRegione(stationId).body() }
            .onFailure { log.warn("Failed to get region for station $stationId", it) }
            .getOrNull()
    }
}
