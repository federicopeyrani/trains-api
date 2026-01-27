package com.federicopeyrani.trainapis.controller

import com.federicopeyrani.trainapis.api.DefaultApi
import com.federicopeyrani.trainapis.model.Run
import com.federicopeyrani.trainapis.model.Trip
import com.federicopeyrani.trainapis.trains.TripsService
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDateTime
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import kotlin.time.ExperimentalTime

@RestController
@OptIn(ExperimentalTime::class)
class TrainsController(
    private val tripsService: TripsService,
) : DefaultApi {

    override fun getDepartures(from: String, date: LocalDateTime?): ResponseEntity<Flow<Trip>> {
        val departures = tripsService.getDepartures(from, date)
        return ResponseEntity.ok(departures)
    }

    override fun getTrips(from: String, to: String, date: LocalDateTime?): ResponseEntity<Flow<Trip>> {
        val trips = tripsService.getTrips(from, to, date)
        return ResponseEntity.ok(trips)
    }

    override suspend fun getRun(runId: String): ResponseEntity<Run> {
        val run = tripsService.getRun(runId)
        return ResponseEntity.ok(run)
    }
}

