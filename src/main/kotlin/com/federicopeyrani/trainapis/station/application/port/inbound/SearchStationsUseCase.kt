package com.federicopeyrani.trainapis.station.application.port.inbound

import com.federicopeyrani.trainapis.station.domain.StationDetails
import kotlinx.coroutines.flow.Flow

fun interface SearchStationsUseCase {

    fun getStations(query: String): Flow<StationDetails>
}
