package com.federicopeyrani.trainapis.trip.adapter.inbound.web

import com.federicopeyrani.trainapis.shared.domain.Station
import com.federicopeyrani.trainapis.shared.infrastructure.web.ApiJson
import com.federicopeyrani.trainapis.trip.application.port.inbound.TripSearchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import org.springframework.http.codec.ServerSentEvent
import com.federicopeyrani.trainapis.trip.domain.DestinationStop
import com.federicopeyrani.trainapis.trip.domain.IntermediateStop
import com.federicopeyrani.trainapis.trip.domain.OriginStop
import com.federicopeyrani.trainapis.trip.domain.Run
import com.federicopeyrani.trainapis.trip.domain.RunStatus
import com.federicopeyrani.trainapis.trip.domain.StopStatus
import com.federicopeyrani.trainapis.trip.domain.Timing
import com.federicopeyrani.trainapis.trip.domain.Train
import com.federicopeyrani.trainapis.trip.domain.Trip
import com.federicopeyrani.trainapis.model.DestinationStop as DestinationStopDto
import com.federicopeyrani.trainapis.model.IntermediateStop as IntermediateStopDto
import com.federicopeyrani.trainapis.model.OriginStop as OriginStopDto
import com.federicopeyrani.trainapis.model.Run as RunDto
import com.federicopeyrani.trainapis.model.Station as StationDto
import com.federicopeyrani.trainapis.model.Status as StatusDto
import com.federicopeyrani.trainapis.model.StopStatus as StopStatusDto
import com.federicopeyrani.trainapis.model.Timing as TimingDto
import com.federicopeyrani.trainapis.model.Train as TrainDto
import com.federicopeyrani.trainapis.model.Trip as TripDto
import com.federicopeyrani.trainapis.model.TripSearch as TripSearchDto
import com.federicopeyrani.trainapis.model.TripSearchSummary as TripSearchSummaryDto

/**
 * Collects a trip search into its JSON form, trips sorted by departure.
 */
suspend fun Flow<TripSearchResult>.toDto(): TripSearchDto {
    val trips = mutableListOf<Trip>()
    var unverified = 0
    collect {
        when (it) {
            is TripSearchResult.Match -> trips += it.trip
            is TripSearchResult.Unverified -> unverified++
        }
    }
    return TripSearchDto(unverifiedDepartures = unverified, trips = trips.sortedWith(Trip.ByDeparture).map { it.toDto() })
}

/**
 * Streams a trip search: one unnamed event per trip, then a `summary` event once the search is complete.
 */
fun Flow<TripSearchResult>.toServerSentEvents(): Flow<ServerSentEvent<String>> = flow {
    var unverified = 0
    collect {
        when (it) {
            is TripSearchResult.Match -> emit(ServerSentEvent.builder(ApiJson.encodeToString(it.trip.toDto())).build())
            is TripSearchResult.Unverified -> unverified++
        }
    }
    val summary = TripSearchSummaryDto(unverifiedDepartures = unverified)
    emit(ServerSentEvent.builder(ApiJson.encodeToString(summary)).event("summary").build())
}

/**
 * The trips of this flow sorted by departure, once all of them are known.
 */
fun Flow<Trip>.sortedByDeparture(): Flow<Trip> = flow { toList().sortedWith(Trip.ByDeparture).forEach { emit(it) } }

fun Trip.toDto() = TripDto(
    from = from.toDto(),
    to = to.toDto(),
    lastUpdate = lastUpdate,
    lastUpdateFromProvider = lastUpdateFromProvider,
    run = run?.toDto(),
)

fun Run.toDto() = RunDto(
    id = id.toString(),
    train = train.toDto(),
    status = status.toDto(),
    origin = origin.toDto(),
    destination = destination.toDto(),
    stops = stops.map { it.toDto() },
    lastUpdate = lastUpdate,
    lastUpdateFromProvider = lastUpdateFromProvider,
)

private fun Train.toDto() = TrainDto(
    id = number.toString(),
    origin = origin.toDto(),
    destination = destination.toDto(),
)

private fun Station.toDto() = StationDto(
    id = id.value,
    name = name,
)

private fun OriginStop.toDto() = OriginStopDto(
    station = station.toDto(),
    status = status?.toDto(),
    departureTime = departureTime?.toDto(),
)

private fun DestinationStop.toDto() = DestinationStopDto(
    station = station.toDto(),
    status = status?.toDto(),
    arrivalTime = arrivalTime?.toDto(),
)

private fun IntermediateStop.toDto() = IntermediateStopDto(
    station = station.toDto(),
    status = status?.toDto(),
    arrivalTime = arrivalTime?.toDto(),
    departureTime = departureTime?.toDto(),
)

private fun Timing.toDto() = TimingDto(
    scheduled = scheduled,
    expected = expected,
    actual = actual,
)

private fun StopStatus.toDto() = when (this) {
    StopStatus.NOT_YET_ARRIVED -> StopStatusDto.NOT_YET_ARRIVED
    StopStatus.ARRIVED -> StopStatusDto.ARRIVED
    StopStatus.DEPARTED -> StopStatusDto.DEPARTED
    StopStatus.NOT_YET_DEPARTED -> StopStatusDto.NOT_YET_DEPARTED
}

private fun RunStatus.toDto() = when (this) {
    RunStatus.ON_TIME -> StatusDto.ON_TIME
    RunStatus.DELAYED -> StatusDto.DELAYED
    RunStatus.CANCELLED -> StatusDto.CANCELLED
}
