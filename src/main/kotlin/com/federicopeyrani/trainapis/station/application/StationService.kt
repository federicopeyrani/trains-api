package com.federicopeyrani.trainapis.station.application

import com.federicopeyrani.trainapis.shared.util.log
import com.federicopeyrani.trainapis.station.application.port.inbound.SearchStationsUseCase
import com.federicopeyrani.trainapis.station.application.port.outbound.StationsProvider
import com.federicopeyrani.trainapis.station.domain.StationDetails
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.springframework.stereotype.Service

@Service
class StationService(
    private val stationsProvider: StationsProvider,
) : SearchStationsUseCase {

    companion object {

        private val log = log()
    }

    override fun getStations(query: String): Flow<StationDetails> {
        if (query.isBlank()) return emptyFlow()
        log.info("Getting stations for query '$query'")
        return stationsProvider.getStations(query)
    }
}
