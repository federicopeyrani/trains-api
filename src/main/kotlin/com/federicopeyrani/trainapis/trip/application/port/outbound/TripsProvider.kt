package com.federicopeyrani.trainapis.trip.application.port.outbound

import com.federicopeyrani.trainapis.trip.domain.Departure
import com.federicopeyrani.trainapis.trip.domain.Run
import com.federicopeyrani.trainapis.trip.domain.RunId
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface TripsProvider {

    /**
     * Departure board of station [from] at [date], each entry enriched with its run when available.
     */
    fun getDepartures(from: String, date: Instant): Flow<Departure>

    suspend fun getRun(runId: RunId): Run
}
