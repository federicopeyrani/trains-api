package com.federicopeyrani.trainapis.trip.domain

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration

/**
 * Scheduled/expected/actual times of an event, all expressed in UTC.
 */
data class Timing(
    val scheduled: LocalDateTime,
    val expected: LocalDateTime? = null,
    val actual: LocalDateTime? = null,
) {

    companion object {

        /**
         * Timing of an event expected to happen [delay] after its [scheduled] time.
         */
        fun delayed(scheduled: LocalDateTime, delay: Duration, actual: LocalDateTime?) = Timing(
            scheduled = scheduled,
            expected = scheduled.toInstant(TimeZone.UTC).plus(delay).toLocalDateTime(TimeZone.UTC),
            actual = actual,
        )
    }
}
