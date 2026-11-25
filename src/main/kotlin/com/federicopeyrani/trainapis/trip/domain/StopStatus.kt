package com.federicopeyrani.trainapis.trip.domain

enum class StopStatus {
    NOT_YET_ARRIVED,
    ARRIVED,
    DEPARTED,
    NOT_YET_DEPARTED,

    /**
     * The train no longer calls at this stop (partial cancellation).
     */
    CANCELLED,
}
