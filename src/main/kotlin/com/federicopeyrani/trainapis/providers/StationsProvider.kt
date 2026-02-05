package com.federicopeyrani.trainapis.providers

import com.federicopeyrani.trainapis.model.StationDetails
import kotlinx.coroutines.flow.Flow

interface StationsProvider {

    fun getStations(query: String): Flow<StationDetails>
}
