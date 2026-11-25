package com.federicopeyrani.trainapis.trip.domain

import com.federicopeyrani.trainapis.shared.domain.Station
import com.federicopeyrani.trainapis.shared.domain.StationId
import kotlin.time.Instant

/**
 * An entry of [station]'s departure board, enriched with the train's [run] when it could be fetched.
 *
 * @property destination final destination of the train, or `null` when neither the run nor the board could resolve it
 * @property scheduledArrival scheduled arrival at [destination]
 * @property lastUpdate when the board was fetched
 */
data class Departure(
    val station: Station,
    val destination: Station?,
    val scheduledDeparture: Instant,
    val scheduledArrival: Instant?,
    val lastUpdate: Instant,
    val lastUpdateFromProvider: Instant? = null,
    val run: Run? = null,
) {

    /**
     * Whether this departure takes a passenger from [station] to [to]. Only the run's calls can tell: without the run
     * the answer is [Serves.UNKNOWN].
     */
    fun serves(to: StationId) = when {
        run == null -> Serves.UNKNOWN
        run.serves(station.id, to) -> Serves.YES
        else -> Serves.NO
    }

    /**
     * The journey from [station] to the train's final destination, or `null` when the destination is unknown.
     *
     * When the run is known, both ends come from the run's calls (live status and timings); otherwise only the
     * board's scheduled times are available.
     */
    fun toTrip(): Trip? = when {
        run != null -> trip(to = run.destination)
        destination != null -> trip(to = DestinationStop(station = destination, arrivalTime = scheduledArrival?.let { Timing(it) }))
        else -> null
    }

    /**
     * The journey from [station] to [to], which this departure must [serve][serves].
     */
    fun toTrip(to: StationId): Trip {
        check(serves(to) == Serves.YES) { "Departure of ${run?.id ?: "an unknown run"} from ${station.id} does not serve '$to'" }
        return trip(to = run!!.arrivalAt(run.indexOf(to)))
    }

    private fun trip(to: DestinationStop): Trip {
        val fromIndex = run?.indexOf(station.id)?.takeIf { run.departsAt(it) }

        return Trip(
            from = fromIndex?.let { run.departureAt(it) }
                ?: OriginStop(station = station, departureTime = Timing(scheduledDeparture)),
            to = to,
            lastUpdate = lastUpdate,
            lastUpdateFromProvider = lastUpdateFromProvider,
            run = run,
        )
    }
}

/**
 * Whether a departure takes a passenger to a station.
 */
enum class Serves {
    YES,
    NO,

    /**
     * The departure's run is not known, so its calls are not either.
     */
    UNKNOWN,
}
