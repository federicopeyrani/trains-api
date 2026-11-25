package com.federicopeyrani.trainapis.trip.domain

import com.federicopeyrani.trainapis.shared.domain.StationId

/**
 * Identifies a run: the train [trainNumber] leaving [originStationId] on the day starting at [departureDate]
 * (epoch milliseconds of midnight, provider time). Serialized as `<origin>_<train>_<date>`.
 */
data class RunId(
    val originStationId: StationId,
    val trainNumber: TrainNumber,
    val departureDate: Long,
) {

    companion object {

        /**
         * @throws InvalidRunIdException if [value] is not a serialized [RunId]
         */
        fun parse(value: String): RunId {
            val parts = value.split("_")
            val origin = parts.getOrNull(0)?.takeIf { it.isNotBlank() }
            val train = parts.getOrNull(1)?.toIntOrNull()
            val date = parts.getOrNull(2)?.toLongOrNull()

            if (parts.size != 3 || origin == null || train == null || date == null) throw InvalidRunIdException(value)

            return RunId(StationId(origin), TrainNumber(train), date)
        }
    }

    override fun toString() = "${originStationId}_${trainNumber}_$departureDate"
}
