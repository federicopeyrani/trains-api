package com.federicopeyrani.trainapis.controller

import com.federicopeyrani.trainapis.api.DefaultApi
import com.federicopeyrani.trainapis.model.Run
import com.federicopeyrani.trainapis.model.Solution
import com.federicopeyrani.trainapis.model.Station
import com.federicopeyrani.trainapis.model.Trip
import com.federicopeyrani.trainapis.trains.TripsService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant

@RestController
class TrainsController(
    private val tripsService: TripsService,
) : DefaultApi {

    override fun autocompleteStations(query: String): ResponseEntity<Flow<Station>> {
        TODO("Not yet implemented")
    }

    @OptIn(ExperimentalTime::class)
    override fun getDepartures(from: String, date: OffsetDateTime?): ResponseEntity<Flow<Trip>> {
        val parsedDate = date?.toInstant()
            ?.toKotlinInstant()
            ?.toLocalDateTime(TimeZone.currentSystemDefault())

        val departures = flow {
            emitAll(tripsService.getDepartures(from, parsedDate).asFlow())
        }

        return ResponseEntity.ok(departures)
    }

    override suspend fun getRunDetails(runId: String): ResponseEntity<Run> {
        TODO("Not yet implemented")
    }

    override fun getSolutions(from: String, to: String): ResponseEntity<Flow<Solution>> {
        TODO("Not yet implemented")
    }
}

