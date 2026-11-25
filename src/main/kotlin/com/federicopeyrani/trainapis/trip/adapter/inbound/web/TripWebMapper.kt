package com.federicopeyrani.trainapis.trip.adapter.inbound.web

import com.federicopeyrani.trainapis.shared.domain.Station
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

fun Trip.toDto() = TripDto(
    from = from.toDto(),
    to = to.toDto(),
    lastUpdate = lastUpdate,
    lastUpdateFromProvider = lastUpdateFromProvider,
    run = run?.toDto(),
)

fun Run.toDto() = RunDto(
    id = id,
    train = train.toDto(),
    status = status.toDto(),
    origin = origin.toDto(),
    destination = destination.toDto(),
    stops = stops.map { it.toDto() },
    lastUpdate = lastUpdate,
    lastUpdateFromProvider = lastUpdateFromProvider,
)

private fun Train.toDto() = TrainDto(
    id = id,
    origin = origin.toDto(),
    destination = destination.toDto(),
)

private fun Station.toDto() = StationDto(
    id = id,
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
