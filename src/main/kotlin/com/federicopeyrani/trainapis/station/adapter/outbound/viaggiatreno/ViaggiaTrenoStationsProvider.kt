package com.federicopeyrani.trainapis.station.adapter.outbound.viaggiatreno

import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoStationsApiWrapper
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.model.StazioneDettagliata
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.displayName
import com.federicopeyrani.trainapis.station.application.port.outbound.StationsProvider
import com.federicopeyrani.trainapis.station.domain.GeoLocation
import com.federicopeyrani.trainapis.station.domain.StationDetails
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@OptIn(ExperimentalCoroutinesApi::class)
@Component
class ViaggiaTrenoStationsProvider(
    private val viaggiaTrenoStationsApiWrapper: ViaggiaTrenoStationsApiWrapper,
    @Value($$"${providers.viaggiatreno.concurrency}") private val concurrency: Int,
) : StationsProvider {

    override fun getStations(query: String) = flow {
        viaggiaTrenoStationsApiWrapper.searchStations(query)
            .data
            .asFlow()
            .flatMapMerge(concurrency = concurrency) {
                flow {
                    viaggiaTrenoStationsApiWrapper
                        .getStationDetails(it.id)
                        .data
                        ?.toStationDetails()
                        ?.let { emit(it) }
                }
            }
            .collect { emit(it) }
    }

    private fun StazioneDettagliata.toStationDetails() = StationDetails(
        id = codStazione,
        name = displayName(),
        location = if (lat != null && lon != null) GeoLocation(latitude = lat, longitude = lon) else null,
    )
}
