package com.federicopeyrani.trainapis.trip.domain

import com.federicopeyrani.trainapis.shared.domain.Station
import com.federicopeyrani.trainapis.shared.domain.StationId
import kotlin.time.Instant

/**
 * An entry of [station]'s departure board, enriched with the train's [run] when it could be fetched.
 *
 * @property destination final destination of the train, as advertised on the board
 * @property scheduledArrival scheduled arrival at [destination]
 * @property lastUpdate when the board was fetched
 */
data class Departure(
    val station: Station,
    val destination: Station,
    val scheduledDeparture: Instant,
    val scheduledArrival: Instant?,
    val lastUpdate: Instant,
    val lastUpdateFromProvider: Instant? = null,
    val run: Run? = null,
) {

    /**
     * Whether this departure takes a passenger from [station] to [to]. Unknown runs never qualify.
     */
    fun serves(to: StationId) = run?.serves(station.id, to) == true

    /**
     * The journey from [station] to [to], or to the final destination when [to] is `null`.
     *
     * When the run is known, both ends come from the run's calls (live status and timings); otherwise only the
     * board's scheduled times are available.
     */
    fun toTrip(to: StationId? = null): Trip {
        val fromIndex = run?.indexOf(station.id)?.takeIf { run.departsAt(it) }
        val toIndex = if (to == null) null else run?.indexOf(to)?.takeIf { it >= 0 }

        return Trip(
            from = fromIndex?.let { run.departureAt(it) }
                ?: OriginStop(station = station, departureTime = Timing(scheduledDeparture)),
            to = when {
                run == null -> DestinationStop(station = destination, arrivalTime = scheduledArrival?.let { Timing(it) })
                to == null -> run.destination
                else -> run.arrivalAt(toIndex ?: error("Run ${run.id} does not call at '$to'"))
            },
            lastUpdate = lastUpdate,
            lastUpdateFromProvider = lastUpdateFromProvider,
            run = run,
        )
    }
}
