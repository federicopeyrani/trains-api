package com.federicopeyrani.trainapis.trip.domain

import kotlinx.datetime.LocalDateTime

/**
 * Scheduled/expected/actual times of an event, all expressed in UTC.
 */
data class Timing(
    val scheduled: LocalDateTime,
    val expected: LocalDateTime? = null,
    val actual: LocalDateTime? = null,
)
