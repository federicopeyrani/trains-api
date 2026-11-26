package com.federicopeyrani.trainapis.trip.domain

import kotlin.time.Instant

/**
 * How well the provider is following a run, as of the run's last update. The states are disjoint; which one applies
 * (e.g. how old a detection must be to be [Stale]) is for the provider's adapter to decide.
 */
sealed interface Tracking {

    /**
     * When the provider last detected the train's position.
     */
    val lastDetection: Instant?

    /**
     * The train has been detected recently.
     */
    data class Live(override val lastDetection: Instant) : Tracking

    /**
     * The train has been detected, but not recently: its times and statuses may be behind.
     */
    data class Stale(override val lastDetection: Instant) : Tracking

    /**
     * The train has not been detected, which is still normal.
     */
    data object Pending : Tracking {
        override val lastDetection: Instant? = null
    }

    /**
     * The train has not been detected although it should have been by now.
     */
    data object Missing : Tracking {
        override val lastDetection: Instant? = null
    }

    /**
     * There is nothing left to follow: the train has arrived or is cancelled.
     */
    data class NotTracked(override val lastDetection: Instant? = null) : Tracking
}
