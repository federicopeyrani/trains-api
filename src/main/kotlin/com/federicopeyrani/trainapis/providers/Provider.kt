package com.federicopeyrani.trainapis.providers

import com.federicopeyrani.trainapis.model.Trip
import kotlinx.datetime.LocalDateTime

interface Provider {

   suspend fun getDepartures(from: String, date: LocalDateTime): List<Trip>
}
