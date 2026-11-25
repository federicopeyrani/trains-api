package com.federicopeyrani.trainapis.trip.domain

import com.federicopeyrani.trainapis.shared.domain.Station

data class OriginStop(
    val station: Station,
    val status: StopStatus? = null,
    val departureTime: Timing? = null,
) {

    companion object {

        /**
         * Origin stop whose status follows from whether the train has actually departed.
         */
        fun of(station: Station, departureTime: Timing?, cancelled: Boolean = false) = OriginStop(
            station = station,
            status = when {
                cancelled -> StopStatus.CANCELLED
                departureTime?.actual != null -> StopStatus.DEPARTED
                else -> StopStatus.NOT_YET_DEPARTED
            },
            departureTime = departureTime,
        )
    }
}

data class DestinationStop(
    val station: Station,
    val status: StopStatus? = null,
    val arrivalTime: Timing? = null,
) {

    companion object {

        /**
         * Destination stop whose status follows from whether the train has actually arrived.
         */
        fun of(station: Station, arrivalTime: Timing?, cancelled: Boolean = false) = DestinationStop(
            station = station,
            status = when {
                cancelled -> StopStatus.CANCELLED
                arrivalTime?.actual != null -> StopStatus.ARRIVED
                else -> StopStatus.NOT_YET_ARRIVED
            },
            arrivalTime = arrivalTime,
        )
    }
}

data class IntermediateStop(
    val station: Station,
    val status: StopStatus? = null,
    val arrivalTime: Timing? = null,
    val departureTime: Timing? = null,
) {

    companion object {

        /**
         * Intermediate stop whose status follows from the actual arrival/departure times.
         */
        fun of(station: Station, arrivalTime: Timing?, departureTime: Timing?, cancelled: Boolean = false) = IntermediateStop(
            station = station,
            status = when {
                cancelled -> StopStatus.CANCELLED
                departureTime?.actual != null -> StopStatus.DEPARTED
                arrivalTime?.actual != null -> StopStatus.ARRIVED
                else -> StopStatus.NOT_YET_ARRIVED
            },
            arrivalTime = arrivalTime,
            departureTime = departureTime,
        )
    }
}
