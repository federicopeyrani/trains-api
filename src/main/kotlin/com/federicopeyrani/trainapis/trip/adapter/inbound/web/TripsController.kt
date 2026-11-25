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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Trip searches answer `application/json` with a [TripSearch] (trips plus the count of unverified departures), which
 * the generated [TripsApi] maps. Their `text/event-stream` form ends with a `summary` event, so it has a different
 * shape than the generated method returns: it is mapped by hand here, and the generated mappings are narrowed to JSON
 * so the two never match the same request.
 */
@RestController
class TripsController(
    private val getDeparturesUseCase: GetDeparturesUseCase,
    private val getTripsUseCase: GetTripsUseCase,
    private val getAllTripsUseCase: GetAllTripsUseCase,
    private val getRunUseCase: GetRunUseCase,
) : TripsApi {

    override fun getDepartures(from: String, date: LocalDateTime?): ResponseEntity<Flow<Trip>> {
        val departures = getDeparturesUseCase.getDepartures(StationId(from), date?.asUtcInstant())
        return ResponseEntity.ok(departures.map { it.toDto() })
    }

    @GetMapping(TripsApi.PATH_GET_TRIPS, produces = [MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE])
    override suspend fun getTrips(from: String, to: String, date: LocalDateTime?): ResponseEntity<TripSearch> =
        ResponseEntity.ok(searchTrips(from, to, date).toDto())

    @GetMapping(TripsApi.PATH_GET_TRIPS, produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamTrips(
        @PathVariable from: String,
        @PathVariable to: String,
        @RequestParam(required = false) date: LocalDateTime?,
    ): Flow<ServerSentEvent<String>> = searchTrips(from, to, date).toServerSentEvents()

    @GetMapping(TripsApi.PATH_GET_ALL_TRIPS, produces = [MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE])
    override suspend fun getAllTrips(from: List<String>, to: List<String>, date: LocalDateTime?): ResponseEntity<TripSearch> =
        ResponseEntity.ok(searchAllTrips(from, to, date).toDto())

    @GetMapping(TripsApi.PATH_GET_ALL_TRIPS, produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamAllTrips(
        @RequestParam from: List<String>,
        @RequestParam to: List<String>,
        @RequestParam(required = false) date: LocalDateTime?,
    ): Flow<ServerSentEvent<String>> = searchAllTrips(from, to, date).toServerSentEvents()

    override suspend fun getRun(runId: String): ResponseEntity<Run> {
        val run = getRunUseCase.getRun(runId)
        return ResponseEntity.ok(run.toDto())
    }

    private fun searchTrips(from: String, to: String, date: LocalDateTime?) =
        getTripsUseCase.getTrips(StationId(from), StationId(to), date?.asUtcInstant())

    private fun searchAllTrips(from: List<String>, to: List<String>, date: LocalDateTime?) =
        getAllTripsUseCase.getAllTrips(from.map(::StationId), to.map(::StationId), date?.asUtcInstant())

    /**
     * Request dates carry no offset; like every date returned by the API, they are interpreted as UTC.
     */
    private fun LocalDateTime.asUtcInstant() = toInstant(TimeZone.UTC)
}
