package com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno

import com.federicopeyrani.trainapis.shared.domain.ProviderUnavailableException
import com.federicopeyrani.trainapis.shared.infrastructure.cache.Cache
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.api.StationsApi
import com.federicopeyrani.trainapis.shared.util.log
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
        viaggiaTrenoCall("station search") {
            stationsApi.cercaStazione(query).bodyOrThrow("station search")
        }
    }

    /**
     * Station details, or `null` when ViaggiaTreno has none for [stationId].
     *
     * @throws ProviderUnavailableException if ViaggiaTreno cannot be reached or rejects the request
     */
    suspend fun getStationDetails(stationId: String) = cache.getOrPut(
        key = Cache.Key.of("station-details-$stationId"),
        ttl = { if (it?.localita == null) 10.seconds else 60.minutes }
    ) {
        val regionCode = getRegionCode(stationId).data

        log.info("Fetching station details from ViaggiaTreno for station '$stationId' in region '$regionCode'")

        viaggiaTrenoCall("station details") {
            val response = stationsApi.dettaglioStazione(stationId, regionCode)
            when (response.status) {
                204 -> null
                else -> response.bodyOrThrow("station details")
            }
        }
    }

    private suspend fun getRegionCode(stationId: String) = cache.getOrPut(
        key = Cache.Key.of("station-region-$stationId"),
        ttl = { 60.minutes }
    ) {
        log.info("Fetching region code from ViaggiaTreno for station '$stationId'")

        viaggiaTrenoCall("region lookup") {
            val response = stationsApi.getRegione(stationId)
            when (response.status) {
                // Some stations are associated with the region code `0`, in which case the API returns a 204
                // "No Content"
                204 -> 0
                else -> response.bodyOrThrow("region lookup")
            }
        }
    }
}
