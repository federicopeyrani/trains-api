package com.federicopeyrani.trainapis.trip.application.port.inbound

import com.federicopeyrani.trainapis.shared.domain.StationId
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

fun interface GetAllTripsUseCase {

    /**
     * Trips for every combination of [from] and [to] stations, plus the departures from [from] that could not be
     * checked.
     *
     * @param date departure time to search from, or `null` for "now"
     */
    fun getAllTrips(from: List<StationId>, to: List<StationId>, date: Instant?): Flow<TripSearchResult>
}
