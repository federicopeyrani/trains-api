package com.federicopeyrani.trainapis.trip.adapter.inbound.web

import com.federicopeyrani.trainapis.api.TripsApi
import com.federicopeyrani.trainapis.model.Run
import com.federicopeyrani.trainapis.model.Trip
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetAllTripsUseCase
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetDeparturesUseCase
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetRunUseCase
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetTripsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class TripsController(
    private val getDeparturesUseCase: GetDeparturesUseCase,
    private val getTripsUseCase: GetTripsUseCase,
    private val getAllTripsUseCase: GetAllTripsUseCase,
    private val getRunUseCase: GetRunUseCase,
) : TripsApi {

    override fun getDepartures(from: String, date: LocalDateTime?): ResponseEntity<Flow<Trip>> {
        val departures = getDeparturesUseCase.getDepartures(from, date?.asUtcInstant())
        return ResponseEntity.ok(departures.map { it.toDto() })
    }

    override fun getTrips(from: String, to: String, date: LocalDateTime?): ResponseEntity<Flow<Trip>> {
        val trips = getTripsUseCase.getTrips(from, to, date?.asUtcInstant())
        return ResponseEntity.ok(trips.map { it.toDto() })
    }

    override fun getAllTrips(from: List<String>, to: List<String>, date: LocalDateTime?): ResponseEntity<Flow<Trip>> {
        val trips = getAllTripsUseCase.getAllTrips(from, to, date?.asUtcInstant())
        return ResponseEntity.ok(trips.map { it.toDto() })
    }

    override suspend fun getRun(runId: String): ResponseEntity<Run> {
        val run = getRunUseCase.getRun(runId)
        return ResponseEntity.ok(run.toDto())
    }

    /**
     * Request dates carry no offset; like every date returned by the API, they are interpreted as UTC.
     */
    private fun LocalDateTime.asUtcInstant() = toInstant(TimeZone.UTC)
}
