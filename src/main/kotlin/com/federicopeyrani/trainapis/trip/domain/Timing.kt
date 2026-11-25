package com.federicopeyrani.trainapis.trip.domain

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Scheduled/expected/actual times of an event.
 */
data class Timing(
    val scheduled: Instant,
    val expected: Instant? = null,
    val actual: Instant? = null,
) {

    companion object {

        /**
         * Timing of an event expected to happen [delay] after its [scheduled] time.
         */
        fun delayed(scheduled: Instant, delay: Duration, actual: Instant?) = Timing(
            scheduled = scheduled,
            expected = scheduled + delay,
            actual = actual,
        )
    }
}
