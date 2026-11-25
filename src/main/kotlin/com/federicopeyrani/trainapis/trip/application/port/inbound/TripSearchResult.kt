package com.federicopeyrani.trainapis.trip.application.port.inbound

import com.federicopeyrani.trainapis.trip.domain.Departure
import com.federicopeyrani.trainapis.trip.domain.Trip

/**
 * An item of a trip search: either a trip, or a departure that could not be checked.
 */
sealed interface TripSearchResult {

    /**
     * A trip to one of the searched destinations.
     */
    data class Match(val trip: Trip) : TripSearchResult

    /**
     * A departure from a searched origin whose run is unknown, so it may or may not reach a searched destination.
     * Reported once per departure, whatever the number of destinations.
     */
    data class Unverified(val departure: Departure) : TripSearchResult
}
