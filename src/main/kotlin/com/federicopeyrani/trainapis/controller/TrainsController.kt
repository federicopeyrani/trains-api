package com.federicopeyrani.trainapis.controller

import com.federicopeyrani.trainapis.api.DefaultApi
import com.federicopeyrani.trainapis.model.Trip
import com.federicopeyrani.trainapis.trains.TripsService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.time.ExperimentalTime

@RestController
@OptIn(ExperimentalTime::class)
class TrainsController(
    private val tripsService: TripsService,
) : DefaultApi {

    override fun getDepartures(from: String, date: LocalDateTime?): ResponseEntity<Flow<Trip>> {
        val departures = flow {
            emitAll(tripsService.getDepartures(from, date).asFlow())
        }

        return ResponseEntity.ok(departures)
    }

    override fun getTrips(from: String, to: String, date: LocalDateTime?): ResponseEntity<Flow<Trip>> {
        val trips = tripsService.getTrips(from, to, date)
        return ResponseEntity.ok(trips)
    }

    @GetMapping("/test")
    fun sampleMethod() = SampleData(1, LocalDateTime(2024, 6, 10, 14, 30))

    @Serializable
    data class SampleData(val id: Int, val date: LocalDateTime)
}

