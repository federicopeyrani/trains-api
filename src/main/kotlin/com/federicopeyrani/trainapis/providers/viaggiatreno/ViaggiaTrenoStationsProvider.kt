package com.federicopeyrani.trainapis.providers.viaggiatreno

import com.federicopeyrani.trainapis.providers.StationsProvider
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
            .data
            .asFlow()
            .flatMapMerge(concurrency = 10) {
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
