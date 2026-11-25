package com.federicopeyrani.trainapis.trip.domain

import kotlinx.datetime.LocalDateTime

/**
 * A specific run of a train on a given date. Only one run per train per date exists.
 */
data class Run(
    val id: String,
    val train: Train,
    val status: RunStatus,
    val origin: OriginStop,
    val destination: DestinationStop,
    val stops: List<IntermediateStop>,
    val lastUpdate: LocalDateTime,
    val lastUpdateFromProvider: LocalDateTime? = null,
)
