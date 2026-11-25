package com.federicopeyrani.trainapis.trip.application.port.inbound

import com.federicopeyrani.trainapis.trip.domain.Trip
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.Instant

interface GetDeparturesUseCase {

    fun getDepartures(from: String, date: Instant = Clock.System.now()): Flow<Trip>
}
