package com.federicopeyrani.trainapis.trip.application.port.outbound

import com.federicopeyrani.trainapis.trip.domain.Run
import com.federicopeyrani.trainapis.trip.domain.Trip
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface TripsProvider {

    fun getDepartures(from: String, date: Instant): Flow<Trip>

    fun getTrips(from: String, to: String, date: Instant): Flow<Trip>

    suspend fun getRun(runId: String): Run
}
