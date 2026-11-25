package com.federicopeyrani.trainapis.trip.domain

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Scheduled/expected/actual times of an event. [expected] is a forecast, only given until the event happens.
 */
data class Timing(
    val scheduled: Instant,
    val expected: Instant? = null,
    val actual: Instant? = null,
) {

    companion object {

        /**
         * Timing of an event running [delay] behind schedule: until it happens ([actual] is `null`), it is expected
         * [delay] after its [scheduled] time; once it happened, only the [actual] time matters. Without a [delay]
         * nothing is expected.
         */
        fun delayed(scheduled: Instant, delay: Duration?, actual: Instant?) = Timing(
            scheduled = scheduled,
            expected = if (actual == null) delay?.let { scheduled + it } else null,
            actual = actual,
        )
    }
}
