package com.federicopeyrani.trainapis.providers.viaggiatreno

import com.federicopeyrani.trainapis.model.Run
import com.federicopeyrani.trainapis.model.Station
import com.federicopeyrani.trainapis.model.Timing
import com.federicopeyrani.trainapis.model.Train
import com.federicopeyrani.trainapis.model.Trip
import com.federicopeyrani.trainapis.providers.Provider
import com.federicopeyrani.trainapis.providers.viaggiatreno.client.api.TrainsApi
import com.federicopeyrani.trainapis.providers.viaggiatreno.client.model.AndamentoTreno
import com.federicopeyrani.trainapis.providers.viaggiatreno.client.model.PartenzeArrivi
import com.federicopeyrani.trainapis.utils.log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atDate
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toInstant
import kotlinx.datetime.toJavaZoneId
import kotlinx.datetime.toLocalDateTime
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


/**
 * Client for interacting with the ViaggiaTreno API.
 *
 * This is a wrapper around the auto-generated OpenAPI client that provides
 * a Spring-friendly interface for the ViaggiaTreno API.
 */
@OptIn(ExperimentalTime::class)
@Service
class ViaggiaTrenoClient(
    private val trainsApi: TrainsApi,
    private val stationsCache: StationsCache,
) : Provider {

    companion object {

        private val log = log()

        // Format date as 'Tue Jan 20 2026 22:21:07 GMT+0100
        private val dateFormatter = DateTimeComponents.Format {
            dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED)
            chars(" ")
            monthName(MonthNames.ENGLISH_ABBREVIATED)
            chars(" ")
            byUnicodePattern("dd yyyy HH:mm:ss 'GMT'Z")
        }

        private val timeFormatter = LocalTime.Format {
            hour()
            chars(":")
            minute()
        }

        private val zone = TimeZone.of("Europe/Rome")
    }

    override suspend fun getDepartures(from: String, date: LocalDateTime) = getDeparturesRaw(from, date)
        .take(10)
        .map { trip ->
            val train = getTrain(trip)
            expandTripDetails(from = from, trip = trip, train = train)
        }

    override fun getTrips(from: String, to: String, date: LocalDateTime): Flow<Trip> = flow {
        val departures = getDeparturesRaw(from, date)

        departures
            .asFlow()
            .mapNotNull { departure ->
                val train = getTrain(departure)

                if (train.fermate?.firstOrNull { it.id == to } == null) {
                    log.info("Skipping train ${departure.numeroTreno} as it does not stop at $to")
                    return@mapNotNull null
                }

                log.info("Expanding trip details for train ${departure.numeroTreno} from $from to $to")

                expandTripDetails(from = from, to = to, trip = departure, train = train)
            }
            .collect { emit(it) }
    }

    private suspend fun getDeparturesRaw(from: String, date: LocalDateTime): List<PartenzeArrivi> {
        val formattedDate = date
            .toInstant(zone)
            .format(dateFormatter)

        log.info("Fetching departures from ViaggiaTreno for station '$from' at date '$formattedDate'")

        val response = trainsApi.getPartenze(from, formattedDate)

        log.info("Status code: ${response.status}")
        log.info("Received ${response.body().size} departures from ViaggiaTreno")

        return response.body()
    }

    private suspend fun getTrain(trip: PartenzeArrivi) = trainsApi
        .andamentoTreno(trip.codOrigine, trip.numeroTreno, trip.dataPartenzaTreno)
        .body()

    private suspend fun expandTripDetails(
        from: String,
        to: String? = null,
        trip: PartenzeArrivi,
        train: AndamentoTreno,
    ): Trip {
        val originStop = train.fermate?.firstOrNull() ?: error("Origin stop not found: ${trip.codOrigine}")
        val thisStop = train.fermate.firstOrNull { it.id == from } ?: error("Stop not found for station: $from")

        val destinationStop =
            (if (to != null) train.fermate.firstOrNull { it.id == to } else train.fermate.lastOrNull())
                ?: error("Destination stop not found: ${trip.destinazione}")

        val tripDate = Instant.fromEpochMilliseconds(trip.dataPartenzaTreno).toLocalDateTime(zone)

        val runExpectedArrivalTime = train.orarioArrivo.toOffsetDateTime()
        val tripDepartureTime = trip.compOrarioPartenza.parseAsTimeAt(tripDate.date)

        val destinationTiming = Timing(
            scheduled = runExpectedArrivalTime,
            expected = train.compOrarioArrivoZeroEffettivo
                ?.parseAsTimeAt(tripDate.date)
                ?.toInstant(zone)
                ?.toEpochMilliseconds()
                ?.toOffsetDateTime(),
            actual = destinationStop.arrivoReale?.toOffsetDateTime(),
        )

        return Trip(
            from = Station(
                id = thisStop.id,
                name = thisStop.stazione,
            ),
            to = Station(
                id = destinationStop.id,
                name = destinationStop.stazione,
            ),
            departureTime = Timing(
                scheduled = tripDepartureTime.toInstant(zone).toEpochMilliseconds().toOffsetDateTime(),
                expected = thisStop.partenzaTeorica?.toOffsetDateTime(),
                actual = thisStop.partenzaReale?.toOffsetDateTime(),
            ),
            arrivalTime = destinationTiming,
            run = Run(
                id = "${trip.numeroTreno}_${trip.dataPartenzaTreno}",
                originDepartureTime = Timing(
                    scheduled = train.orarioPartenza.toOffsetDateTime(),
                    expected = originStop.partenzaTeorica?.toOffsetDateTime(),
                    actual = originStop.partenzaReale?.toOffsetDateTime(),
                ),
                destinationArrivalTime = destinationTiming,
                train = Train(
                    id = trip.numeroTreno.toString(),
                    origin = stationsCache.getStationByName(train.origine)
                        ?: error("Station not found: ${train.origine}"),
                    destination = stationsCache.getStationByName(trip.destinazione)
                        ?: error("Station not found: ${trip.destinazione}"),
                ),
            ),
        )
    }

    private fun Long.toOffsetDateTime(): OffsetDateTime {
        return OffsetDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(this),
            zone.toJavaZoneId()
        )
    }

    private fun String.parseAsTimeAt(date: LocalDate): LocalDateTime {
        val time = timeFormatter.parse(this)
        return time.atDate(date)
    }
}

