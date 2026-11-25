package com.federicopeyrani.trainapis.trip.application.port.inbound

import com.federicopeyrani.trainapis.trip.domain.Trip
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.Instant

interface GetTripsUseCase {

    fun getTrips(from: String, to: String, date: Instant = Clock.System.now()): Flow<Trip>

    fun getAllTrips(from: List<String>, to: List<String>, date: Instant = Clock.System.now()): Flow<Trip>
}
