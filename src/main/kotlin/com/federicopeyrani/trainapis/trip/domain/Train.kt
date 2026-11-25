package com.federicopeyrani.trainapis.trip.domain

import com.federicopeyrani.trainapis.shared.domain.Station

/**
 * A train; the same train id can be used across different runs.
 */
data class Train(
    val id: String,
    val origin: Station,
    val destination: Station,
)
