package com.federicopeyrani.trainapis.trip.domain

import com.federicopeyrani.trainapis.shared.domain.Station

data class OriginStop(
    val station: Station,
    val status: StopStatus? = null,
    val departureTime: Timing? = null,
)

data class DestinationStop(
    val station: Station,
    val status: StopStatus? = null,
    val arrivalTime: Timing? = null,
)

data class IntermediateStop(
    val station: Station,
    val status: StopStatus? = null,
    val arrivalTime: Timing? = null,
    val departureTime: Timing? = null,
)
