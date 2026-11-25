package com.federicopeyrani.trainapis.trip.application.port.inbound

import com.federicopeyrani.trainapis.trip.domain.Trip
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

fun interface GetDeparturesUseCase {

    /**
     * @param date departure time to search from, or `null` for "now"
     */
    fun getDepartures(from: String, date: Instant?): Flow<Trip>
}
