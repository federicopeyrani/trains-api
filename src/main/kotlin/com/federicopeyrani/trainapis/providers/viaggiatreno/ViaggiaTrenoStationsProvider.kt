package com.federicopeyrani.trainapis.providers.viaggiatreno

import com.federicopeyrani.trainapis.providers.StationsProvider
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
                        ?.toStation()
                        ?.let { emit(it) }
                }
            }
            .collect { emit(it) }
    }
}
