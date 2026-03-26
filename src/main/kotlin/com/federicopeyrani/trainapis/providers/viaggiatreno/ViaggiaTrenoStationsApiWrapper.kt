package com.federicopeyrani.trainapis.providers.viaggiatreno

import com.federicopeyrani.trainapis.data.Cache
import com.federicopeyrani.trainapis.providers.viaggiatreno.client.api.StationsApi
import com.federicopeyrani.trainapis.utils.log
import kotlinx.coroutines.CancellationException
import org.springframework.stereotype.Component
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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
        ttl = { 60.minutes }
    ) {
        log.info("Searching stations from ViaggiaTreno for query '$query'")
        stationsApi.cercaStazione(query).body()
    }

    suspend fun getStationDetails(stationId: String) = cache.getOrPut(
        key = Cache.Key.of("station-details-$stationId"),
        ttl = { if (it?.localita == null) 10.seconds else 60.minutes }
    ) {
        log.info("Fetching station details from ViaggiaTreno for station '$stationId'")

        val regionCode = getRegionCode(stationId).data ?: run {
            log.warn("Failed to get region code for station $stationId, cannot fetch station details")
            return@getOrPut null
        }

        log.info("Got region code '$regionCode' for station '$stationId', fetching station details")

        stationsApi.dettaglioStazione(stationId, regionCode).body()
    }

    private suspend fun getRegionCode(stationId: String) = cache.getOrPut(
        key = Cache.Key.of("station-region-$stationId"),
        ttl = { 60.minutes }
    ) {
        log.info("Fetching region code from ViaggiaTreno for station '$stationId'")

        runCatching { stationsApi.getRegione(stationId) }
            .mapCatching {
                when (it.status) {
                    // Some stations are associated with the region code `0`, in which case the API returns a 204
                    // "No Content"
                    204 -> 0
                    else -> it.body()
                }
            }
            .onFailure {
                if (it is CancellationException) throw it
                log.warn("Failed to get region for station $stationId", it)
            }
            .getOrNull()
    }
}
