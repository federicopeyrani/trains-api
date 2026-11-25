package com.federicopeyrani.trainapis.trip.adapter.inbound.web

import com.federicopeyrani.trainapis.api.TripsApi
import com.federicopeyrani.trainapis.model.Run
import com.federicopeyrani.trainapis.model.Trip
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
import kotlin.time.ExperimentalTime

@RestController
@OptIn(ExperimentalTime::class)
class TripsController(
    private val getDeparturesUseCase: GetDeparturesUseCase,
    private val getTripsUseCase: GetTripsUseCase,
    private val getRunUseCase: GetRunUseCase,
) : TripsApi {

    private val zone = TimeZone.currentSystemDefault()

    override fun getDepartures(from: String, date: LocalDateTime?): ResponseEntity<Flow<Trip>> {
        val departures = date
            ?.toInstant(zone)
            ?.let { getDeparturesUseCase.getDepartures(from, it) }
            ?: getDeparturesUseCase.getDepartures(from)
        return ResponseEntity.ok(departures.map { it.toDto() })
    }

    override fun getTrips(from: String, to: String, date: LocalDateTime?): ResponseEntity<Flow<Trip>> {
        val trips = date
            ?.toInstant(zone)
            ?.let { getTripsUseCase.getTrips(from, to, it) }
            ?: getTripsUseCase.getTrips(from, to)
        return ResponseEntity.ok(trips.map { it.toDto() })
    }

    override fun getAllTrips(from: List<String>, to: List<String>, date: LocalDateTime?): ResponseEntity<Flow<Trip>> {
        val trips = date
            ?.toInstant(zone)
            ?.let { getTripsUseCase.getAllTrips(from, to, it) }
            ?: getTripsUseCase.getAllTrips(from, to)
        return ResponseEntity.ok(trips.map { it.toDto() })
    }

    override suspend fun getRun(runId: String): ResponseEntity<Run> {
        val run = getRunUseCase.getRun(runId)
        return ResponseEntity.ok(run.toDto())
    }
}
