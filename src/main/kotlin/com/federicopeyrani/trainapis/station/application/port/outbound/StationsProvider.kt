package com.federicopeyrani.trainapis.station.application.port.outbound

import com.federicopeyrani.trainapis.station.domain.StationDetails
import kotlinx.coroutines.flow.Flow

interface StationsProvider {

    fun getStations(query: String): Flow<StationDetails>
}
