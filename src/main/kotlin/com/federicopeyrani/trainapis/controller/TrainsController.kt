package com.federicopeyrani.trainapis.controller

import com.federicopeyrani.trainapis.api.DefaultApi
import com.federicopeyrani.trainapis.model.Run
import com.federicopeyrani.trainapis.model.StationDetails
import com.federicopeyrani.trainapis.model.Trip
import com.federicopeyrani.trainapis.trains.TripsService
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import kotlin.time.ExperimentalTime

@RestController
@OptIn(ExperimentalTime::class)
class TrainsController(
    private val tripsService: TripsService,
) : DefaultApi {

    private val zone = TimeZone.currentSystemDefault()

    override fun getStations(query: String): ResponseEntity<Flow<StationDetails>> {
        val stations = tripsService.getStations(query)
        return ResponseEntity.ok(stations)
    }

    override fun getDepartures(from: String, date: LocalDateTime?): ResponseEntity<Flow<Trip>> {
        val departures = date
            ?.toInstant(zone)
            ?.let { tripsService.getDepartures(from, it) }
            ?: tripsService.getDepartures(from)
        return ResponseEntity.ok(departures)
    }

    override fun getTrips(from: String, to: String, date: LocalDateTime?): ResponseEntity<Flow<Trip>> {
        val trips = date
            ?.toInstant(zone)
            ?.let { tripsService.getTrips(from, to, it) }
            ?: tripsService.getTrips(from, to)
        return ResponseEntity.ok(trips)
    }

    override fun getAllTrips(from: List<String>, to: List<String>, date: LocalDateTime?): ResponseEntity<Flow<Trip>> {
        val trips = date
            ?.toInstant(zone)
            ?.let { tripsService.getAllTrips(from, to, it) }
            ?: tripsService.getAllTrips(from, to)
        return ResponseEntity.ok(trips)
    }

    override suspend fun getRun(runId: String): ResponseEntity<Run> {
        val run = tripsService.getRun(runId)
        return ResponseEntity.ok(run)
    }
}

