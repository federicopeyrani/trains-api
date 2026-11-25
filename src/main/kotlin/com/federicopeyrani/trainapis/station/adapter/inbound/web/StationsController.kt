package com.federicopeyrani.trainapis.station.adapter.inbound.web

import com.federicopeyrani.trainapis.api.StationsApi
import com.federicopeyrani.trainapis.model.StationDetails
import com.federicopeyrani.trainapis.station.application.port.inbound.SearchStationsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class StationsController(
    private val searchStations: SearchStationsUseCase,
) : StationsApi {

    override fun getStations(query: String): ResponseEntity<Flow<StationDetails>> {
        val stations = searchStations.getStations(query).map { it.toDto() }
        return ResponseEntity.ok(stations)
    }
}
