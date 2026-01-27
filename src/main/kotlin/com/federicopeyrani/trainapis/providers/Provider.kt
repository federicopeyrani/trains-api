package com.federicopeyrani.trainapis.providers

import com.federicopeyrani.trainapis.model.Run
import com.federicopeyrani.trainapis.model.Trip
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDateTime

interface Provider {

    fun getDepartures(from: String, date: LocalDateTime): Flow<Trip>

    fun getTrips(from: String, to: String, date: LocalDateTime): Flow<Trip>

    suspend fun getRun(runId: String): Run
}
