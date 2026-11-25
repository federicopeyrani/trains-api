package com.federicopeyrani.trainapis.trip.domain

import kotlinx.datetime.LocalDateTime

/**
 * A journey between two stations, optionally enriched with the full [run] of the train.
 */
data class Trip(
    val from: OriginStop,
    val to: DestinationStop,
    val lastUpdate: LocalDateTime,
    val lastUpdateFromProvider: LocalDateTime? = null,
    val run: Run? = null,
)
