package com.federicopeyrani.trainapis.shared.domain

/**
 * Identifies a station (e.g. `S01700`).
 */
@JvmInline
value class StationId(val value: String) {

    override fun toString() = value
}

/**
 * Minimal station reference shared by every feature (shared kernel).
 */
data class Station(
    val id: StationId,
    val name: String,
)
