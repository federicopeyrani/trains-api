package com.federicopeyrani.trainapis.trip.domain

import kotlin.time.Instant

/**
 * A journey between two stations, optionally enriched with the full [run] of the train.
 */
data class Trip(
    val from: OriginStop,
    val to: DestinationStop,
    val lastUpdate: Instant,
    val lastUpdateFromProvider: Instant? = null,
    val run: Run? = null,
) {

    companion object {

        /**
         * Earliest scheduled departure first, trips without a departure time last, then by train number.
         */
        val ByDeparture: Comparator<Trip> = compareBy<Trip, Instant?>(nullsLast()) { it.from.departureTime?.scheduled }
            .thenBy { it.run?.train?.number?.value }
    }
}
