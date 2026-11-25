package com.federicopeyrani.trainapis.trip.adapter.inbound.web

import com.federicopeyrani.trainapis.api.TripsApi
import com.federicopeyrani.trainapis.model.Run
import com.federicopeyrani.trainapis.model.Trip
import com.federicopeyrani.trainapis.model.TripSearch
import com.federicopeyrani.trainapis.shared.domain.StationId
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetAllTripsUseCase
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetDeparturesUseCase
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetRunUseCase
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetTripsUseCase
import com.federicopeyrani.trainapis.trip.domain.Trip as TripDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import kotlin.time.Instant

/**
 * JSON responses are complete lists, sorted by departure ([TripDomain.ByDeparture]); `text/event-stream` responses send
 * each trip as soon as it is found, so they are in no particular order. Trip searches answer JSON with a [TripSearch]
 * (trips plus the count of unverified departures), and their stream ends with a `summary` event.
 *
 * The two forms differ, but the generated [TripsApi] has one method per operation: the stream forms are mapped by hand
 * here, and the generated mappings are narrowed to JSON so the two never match the same request.
 */
@RestController
class TripsController(
    private val getDeparturesUseCase: GetDeparturesUseCase,
    private val getTripsUseCase: GetTripsUseCase,
    private val getAllTripsUseCase: GetAllTripsUseCase,
    private val getRunUseCase: GetRunUseCase,
) : TripsApi {

    @GetMapping(TripsApi.PATH_GET_DEPARTURES, produces = [MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE])
    override fun getDepartures(from: String, date: Instant?): ResponseEntity<Flow<Trip>> =
        ResponseEntity.ok(departures(from, date).sortedByDeparture().map { it.toDto() })

    @GetMapping(TripsApi.PATH_GET_DEPARTURES, produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamDepartures(
        @PathVariable from: String,
        @RequestParam(required = false) date: Instant?,
    ): Flow<Trip> = departures(from, date).map { it.toDto() }

    @GetMapping(TripsApi.PATH_GET_TRIPS, produces = [MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE])
    override suspend fun getTrips(from: String, to: String, date: Instant?): ResponseEntity<TripSearch> =
        ResponseEntity.ok(searchTrips(from, to, date).toDto())

    @GetMapping(TripsApi.PATH_GET_TRIPS, produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamTrips(
        @PathVariable from: String,
        @PathVariable to: String,
        @RequestParam(required = false) date: Instant?,
    ): Flow<ServerSentEvent<String>> = searchTrips(from, to, date).toServerSentEvents()

    @GetMapping(TripsApi.PATH_GET_ALL_TRIPS, produces = [MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE])
    override suspend fun getAllTrips(from: List<String>, to: List<String>, date: Instant?): ResponseEntity<TripSearch> =
        ResponseEntity.ok(searchAllTrips(from, to, date).toDto())

    @GetMapping(TripsApi.PATH_GET_ALL_TRIPS, produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamAllTrips(
        @RequestParam from: List<String>,
        @RequestParam to: List<String>,
        @RequestParam(required = false) date: Instant?,
    ): Flow<ServerSentEvent<String>> = searchAllTrips(from, to, date).toServerSentEvents()

    override suspend fun getRun(runId: String): ResponseEntity<Run> {
        val run = getRunUseCase.getRun(runId)
        return ResponseEntity.ok(run.toDto())
    }

    private fun departures(from: String, date: Instant?) = getDeparturesUseCase.getDepartures(StationId(from), date)

    private fun searchTrips(from: String, to: String, date: Instant?) =
        getTripsUseCase.getTrips(StationId(from), StationId(to), date)

    private fun searchAllTrips(from: List<String>, to: List<String>, date: Instant?) =
        getAllTripsUseCase.getAllTrips(from.map(::StationId), to.map(::StationId), date)
}
