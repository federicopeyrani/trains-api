package com.federicopeyrani.trainapis.trip.application.port.inbound

import com.federicopeyrani.trainapis.shared.domain.StationId
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

fun interface GetTripsUseCase {

    /**
     * Trips from [from] to [to], plus the departures from [from] that could not be checked.
     *
     * @param date departure time to search from, or `null` for "now"
     */
    fun getTrips(from: StationId, to: StationId, date: Instant?): Flow<TripSearchResult>
}
