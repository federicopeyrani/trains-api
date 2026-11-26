package com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno

import com.federicopeyrani.trainapis.shared.domain.ProviderUnavailableException
import com.federicopeyrani.trainapis.shared.infrastructure.cache.Cache
import com.federicopeyrani.trainapis.shared.infrastructure.cache.CacheElement
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.api.TrainsApi
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.model.AndamentoTreno
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.model.PartenzeArrivi
import com.federicopeyrani.trainapis.shared.util.log
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.offsetAt
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.format.format
import kotlinx.datetime.toLocalDateTime
import org.springframework.stereotype.Component
import kotlin.time.Instant
import kotlin.time.toKotlinDuration

@Component
class ViaggiaTrenoTrainsApiWrapper(
    private val trainsApi: TrainsApi,
    private val cache: Cache,
    properties: ViaggiaTrenoProperties = ViaggiaTrenoProperties(),
) {

    private val ttls = properties.cache

    companion object {

        private val log = log()

        // Format date as 'Tue Jan 20 2026 22:21:07 GMT+0100' (JavaScript's `Date.toString()`), with the offset of
        // Europe/Rome at that date, so '+0200' during summer time
        private val dateFormatter = DateTimeComponents.Format {
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
            chars(" GMT")
            offset(UtcOffset.Formats.FOUR_DIGITS)
        }

        private val zone = TimeZone.of("Europe/Rome")
    }

    /**
     * Departure board of station [from] at [date].
     *
     * The date is truncated to the minute so that requests for "now" share cache entries instead of creating a new
     * one per call.
     */
    suspend fun getDepartures(from: String, date: Instant): CacheElement<List<PartenzeArrivi>> {
        val minute = Instant.fromEpochSeconds(date.epochSeconds - date.epochSeconds.mod(60L))
        return cache.getOrPut(
            key = Cache.Key.of("departures-$from-$minute"),
            ttl = { ttls.departures.toKotlinDuration() },
        ) { fetchDepartures(from, minute) }
    }

    private suspend fun fetchDepartures(from: String, date: Instant): List<PartenzeArrivi> {
        val formattedDate = dateFormatter.format {
            setDateTimeOffset(date.toLocalDateTime(zone), zone.offsetAt(date))
        }

        log.info("Fetching departures from ViaggiaTreno for station '$from' at date '$formattedDate'")

        return viaggiaTrenoCall("departures") {
            trainsApi
                .getPartenze(from, formattedDate)
                .bodyOrThrow("departures")
        }
    }

    suspend fun getTrain(trip: PartenzeArrivi) =
        getTrain(trip.codOrigine, trip.numeroTreno, trip.dataPartenzaTreno)

    /**
     * Like [getTrain], but logs and returns `null` instead of failing, for best-effort enrichment.
     */
    suspend fun getTrainOrNull(trip: PartenzeArrivi): CacheElement<AndamentoTreno>? =
        try {
            getTrain(trip).let { element -> element.data?.let { CacheElement(it, element.timestamp, element.ttl) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Failed to fetch train details for train ${trip.numeroTreno}: ${e.message}")
            null
        }

    /**
     * Status of train [trainId] departed from [from] on [date], or `null` when ViaggiaTreno does not know it.
     *
     * @throws ProviderUnavailableException if ViaggiaTreno cannot be reached or rejects the request
     */
    suspend fun getTrain(
        from: String,
        trainId: Int,
        date: Long,
    ) = cache.getOrPut(
        key = Cache.Key.of<AndamentoTreno?>("train-${from}-${trainId}-${date}"),
        ttl = { if (it == null) ttls.trainUnknown.toKotlinDuration() else ttls.train.toKotlinDuration() }
    ) {
        log.info("Fetching train status from ViaggiaTreno API for trainId=${trainId} on date=$date at stationId=$from")

        viaggiaTrenoCall("train status") {
            val response = trainsApi.andamentoTreno(from, trainId, date)
            when (response.status) {
                204 -> null
                else -> response.bodyOrThrow("train status")
            }
        }
    }
}
