package com.federicopeyrani.trainapis.trains

import com.federicopeyrani.trainapis.model.Trip
import com.federicopeyrani.trainapis.providers.Provider
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.springframework.stereotype.Service
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Service
class TripsService(
    private val provider: Provider,
) {

    @OptIn(ExperimentalTime::class)
    suspend fun getDepartures(from: String, date: LocalDateTime?): List<Trip> {
        return provider.getDepartures(from, date ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()))
    }
}
