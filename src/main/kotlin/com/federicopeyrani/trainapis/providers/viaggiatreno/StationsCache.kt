package com.federicopeyrani.trainapis.providers.viaggiatreno

import com.federicopeyrani.trainapis.model.Station
import com.federicopeyrani.trainapis.providers.viaggiatreno.client.api.StationsApi
import com.federicopeyrani.trainapis.utils.log
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component

@Component
class StationsCache(
    private val stationsApi: StationsApi,
) {

    companion object {

        private val log = log()
    }

    @Cacheable("stations")
    suspend fun getStationByName(name: String): Station? {
        log.info("Getting station with id: $name")

        val response = stationsApi.cercaStazione(name)
        val stationDto = response.body()
            .firstOrNull { it.nomeLungo == name }
            ?: run {
                log.info("Station with id $name not found")
                return null
            }

        log.info("Station found: ${stationDto})")

        return Station(
            id = stationDto.id,
            name = stationDto.nomeBreve
        )
    }
}
