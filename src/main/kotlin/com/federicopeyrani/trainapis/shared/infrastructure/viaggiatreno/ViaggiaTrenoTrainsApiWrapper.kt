package com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno

import com.federicopeyrani.trainapis.shared.infrastructure.cache.Cache
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.api.TrainsApi
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.model.PartenzeArrivi
import com.federicopeyrani.trainapis.shared.util.log
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import org.springframework.stereotype.Component
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Component
class ViaggiaTrenoTrainsApiWrapper(
    private val trainsApi: TrainsApi,
    private val cache: Cache,
) {

    companion object {

        private val log = log()

        // Format date as 'Tue Jan 20 2026 22:21:07 GMT+0100'
        private val dateFormatter = LocalDateTime.Format {
            dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED)
            char(' ')
            monthName(MonthNames.ENGLISH_ABBREVIATED)
            char(' ')
            day()
            char(' ')
            year()
            char(' ')
            hour()
            char(':')
            minute()
            char(':')
            second()
            char(' ')
            chars("GMT+0100")
        }

        private val zone = TimeZone.of("Europe/Rome")
    }

    suspend fun getDepartures(
        from: String,
        date: Instant,
    ) = cache.getOrPut(
        key = Cache.Key.of("departures-$from-${date}"),
        ttl = { 10.minutes }
    ) {
        val formattedDate = date.toLocalDateTime(zone).format(dateFormatter)

        log.info("Fetching departures from ViaggiaTreno for station '$from' at date '$formattedDate'")

        trainsApi
            .getPartenze(from, formattedDate)
            .body()
    }

    suspend fun getTrain(trip: PartenzeArrivi) =
        getTrain(trip.codOrigine, trip.numeroTreno, trip.dataPartenzaTreno)

    suspend fun getTrainOrNull(trip: PartenzeArrivi) =
        runCatching { getTrain(trip) }
            .onFailure { log.warn("Failed to fetch train details for train ${trip.numeroTreno}", it) }
            .getOrNull()

    suspend fun getTrain(
        from: String,
        trainId: Int,
        date: Long,
    ) = cache.getOrPut(
        key = Cache.Key.of("train-${from}-${trainId}-${date}"),
        ttl = { 10.minutes }
    ) {
        log.info("Fetching train status from ViaggiaTreno API for trainId=${trainId} on date=$date at stationId=$from")

        trainsApi
            .andamentoTreno(from, trainId, date)
            .body()
    }
}
