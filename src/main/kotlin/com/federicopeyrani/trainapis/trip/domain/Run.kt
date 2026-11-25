package com.federicopeyrani.trainapis.trip.domain

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
     * Index of the first call at [stationId], or `-1` if the run does not call there.
     */
    fun indexOf(stationId: String): Int {
        if (origin.station.id == stationId) return 0
        val stop = stops.indexOfFirst { it.station.id == stationId }
        if (stop >= 0) return stop + 1
        return if (destination.station.id == stationId) destinationIndex else -1
    }

    /**
     * Whether a passenger can travel from [from] to [to] on this run, i.e. it calls at both, in that order.
     */
    fun serves(from: String, to: String): Boolean {
        val fromIndex = indexOf(from)
        return fromIndex >= 0 && indexOf(to) > fromIndex
    }

    /**
     * The call at [index] seen as the start of a journey.
     */
    fun departureAt(index: Int): OriginStop = when (index) {
        0 -> origin
        destinationIndex -> OriginStop(destination.station, destination.status)
        else -> stops[index - 1].let { OriginStop(it.station, it.status, it.departureTime) }
    }

    /**
     * The call at [index] seen as the end of a journey.
     */
    fun arrivalAt(index: Int): DestinationStop = when (index) {
        0 -> DestinationStop(origin.station, origin.status)
        destinationIndex -> destination
        else -> stops[index - 1].let { DestinationStop(it.station, it.status, it.arrivalTime) }
    }
}
