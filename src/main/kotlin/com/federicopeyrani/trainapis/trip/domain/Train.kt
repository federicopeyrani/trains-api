package com.federicopeyrani.trainapis.trip.domain

import com.federicopeyrani.trainapis.shared.domain.Station

/**
 * Number of a train (e.g. `2185`); the same number is used by the train's runs on different days.
 */
@JvmInline
value class TrainNumber(val value: Int) {

    override fun toString() = value.toString()
}

/**
 * A train; the same train is used across different runs.
 */
data class Train(
    val number: TrainNumber,
    val origin: Station,
    val destination: Station,
)
