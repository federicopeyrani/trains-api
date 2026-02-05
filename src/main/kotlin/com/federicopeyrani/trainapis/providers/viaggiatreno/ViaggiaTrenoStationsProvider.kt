package com.federicopeyrani.trainapis.providers.viaggiatreno

import com.federicopeyrani.trainapis.model.StationDetails
import com.federicopeyrani.trainapis.model.StationDetailsAllOfLocation
import com.federicopeyrani.trainapis.providers.StationsProvider
import com.federicopeyrani.trainapis.providers.viaggiatreno.client.model.StazioneDettagliata
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import org.springframework.stereotype.Component

@OptIn(ExperimentalCoroutinesApi::class)
@Component
class ViaggiaTrenoStationsProvider(
    private val viaggiaTrenoStationsApiWrapper: ViaggiaTrenoStationsApiWrapper,
) : StationsProvider {

    override fun getStations(query: String) = flow {
        viaggiaTrenoStationsApiWrapper.searchStations(query)
            .asFlow()
            .flatMapMerge(concurrency = 10) {
                flow {
                    val details = viaggiaTrenoStationsApiWrapper.getStationDetails(it.id)?.toDetails()
                        ?: StationDetails(id = it.id, name = it.nomeBreve)
                    emit(details)
                }
            }
            .collect { emit(it) }
    }

    private fun StazioneDettagliata.toDetails() = StationDetails(
        id = localita.id,
        name = localita.nomeBreve,
        location = if (lat != null && lon != null)
            StationDetailsAllOfLocation(
                latitude = lat.toFloat(),
                longitude = lon.toFloat(),
            ) else null
    )
}
