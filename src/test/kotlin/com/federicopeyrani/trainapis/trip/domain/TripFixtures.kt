package com.federicopeyrani.trainapis.trip.domain

import com.federicopeyrani.trainapis.shared.domain.StationId
import com.federicopeyrani.trainapis.shared.domain.Station
import kotlin.time.Instant

/**
 * Builders for trip domain objects. A run calls at [stations] in order, one hour apart, starting at 08:00 UTC.
 */
object TripFixtures {

    val updatedAt: Instant = Instant.parse("2026-01-23T07:00:00Z")

    fun id(value: String) = StationId(value)

    fun station(id: String) = Station(id = id(id), name = "Station $id")

    private fun at(hour: Int) = Instant.parse("2026-01-23T%02d:00:00Z".format(hour))

    fun run(vararg stations: String, trainNumber: Int = 100): Run {
        require(stations.size >= 2)
        val lastHour = 8 + stations.lastIndex

        return Run(
            id = RunId(id(stations.first()), TrainNumber(trainNumber), 1769122800000),
            train = Train(TrainNumber(trainNumber), station(stations.first()), station(stations.last())),
            status = RunStatus.ON_TIME,
            origin = OriginStop.of(station(stations.first()), Timing(at(8))),
            destination = DestinationStop.of(station(stations.last()), Timing(at(lastHour))),
            stops = stations.drop(1).dropLast(1).mapIndexed { index, id ->
                val hour = 9 + index
                IntermediateStop.of(station(id), arrivalTime = Timing(at(hour)), departureTime = Timing(at(hour)))
            },
            lastUpdate = updatedAt,
        )
    }

    fun departure(from: String, run: Run?) = Departure(
        station = station(from),
        destination = run?.destination?.station ?: station("board-destination"),
        scheduledDeparture = at(8),
        scheduledArrival = null,
        lastUpdate = updatedAt,
        run = run,
    )
}
