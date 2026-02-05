package com.federicopeyrani.trainapis.providers

import com.federicopeyrani.trainapis.model.Run
import com.federicopeyrani.trainapis.model.Trip
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface TripsProvider {

    fun getDepartures(from: String, date: Instant): Flow<Trip>

    fun getTrips(from: String, to: String, date: Instant): Flow<Trip>

    suspend fun getRun(runId: String): Run
}
