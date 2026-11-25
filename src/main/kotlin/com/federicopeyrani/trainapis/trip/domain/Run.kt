package com.federicopeyrani.trainapis.trip.domain

import com.federicopeyrani.trainapis.shared.domain.Station
import com.federicopeyrani.trainapis.shared.domain.StationId
import kotlin.time.Instant

/**
 * A specific run of a train on a given date. Only one run per train per date exists.
 *
 * Calls are indexed in order: `0` is [origin], `1..stops.size` are [stops], `stops.size + 1` is [destination].
 */
data class Run(
    val id: RunId,
    val train: Train,
    val status: RunStatus,
    val origin: OriginStop,
    val destination: DestinationStop,
    val stops: List<IntermediateStop>,
    val lastUpdate: Instant,
    val lastUpdateFromProvider: Instant? = null,
) {

    private val destinationIndex get() = stops.size + 1

    /**
     * Index of the first call at [stationId], or `-1` if the run does not call there. Cancelled calls do not count: the
     * train does not stop there.
     */
    fun indexOf(stationId: StationId): Int {
        fun callsAt(station: Station, status: StopStatus?) = station.id == stationId && status != StopStatus.CANCELLED

        if (callsAt(origin.station, origin.status)) return 0
        val stop = stops.indexOfFirst { callsAt(it.station, it.status) }
        if (stop >= 0) return stop + 1
        return if (callsAt(destination.station, destination.status)) destinationIndex else -1
    }

    /**
     * Whether a passenger can travel from [from] to [to] on this run, i.e. it calls at both, in that order.
     */
    fun serves(from: StationId, to: StationId): Boolean {
        val fromIndex = indexOf(from)
        return fromIndex >= 0 && indexOf(to) > fromIndex
    }

    /**
     * Whether a journey can start at the call at [index]: any call but the destination.
     */
    fun departsAt(index: Int) = index in 0..<destinationIndex

    /**
     * The call at [index] seen as the start of a journey.
     */
    fun departureAt(index: Int): OriginStop {
        require(departsAt(index)) { "Run $id does not depart from call $index" }
        return if (index == 0) origin else stops[index - 1].let { OriginStop(it.station, it.status, it.departureTime) }
    }

    /**
     * The call at [index] seen as the end of a journey: any call but the origin.
     */
    fun arrivalAt(index: Int): DestinationStop {
        require(index in 1..destinationIndex) { "Run $id does not arrive at call $index" }
        return if (index == destinationIndex) destination
        else stops[index - 1].let { DestinationStop(it.station, it.status, it.arrivalTime) }
    }
}
