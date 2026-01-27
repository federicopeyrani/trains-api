package com.federicopeyrani.trainapis.trains

import com.federicopeyrani.trainapis.providers.Provider
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.springframework.stereotype.Service
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Service
@OptIn(ExperimentalTime::class)
class TripsService(
    private val provider: Provider,
) {

    fun getDepartures(from: String, date: LocalDateTime?) =
        provider.getDepartures(from, date ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()))

    fun getTrips(from: String, to: String, date: LocalDateTime?) =
        provider.getTrips(from, to, date ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()))

    suspend fun getRun(runId: String) = provider.getRun(runId)
}
