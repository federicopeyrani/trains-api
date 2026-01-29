package com.federicopeyrani.trainapis.providers.viaggiatreno

import com.federicopeyrani.trainapis.model.DestinationStop
import com.federicopeyrani.trainapis.model.IntermediateStop
import com.federicopeyrani.trainapis.model.OriginStop
import com.federicopeyrani.trainapis.model.Run
import com.federicopeyrani.trainapis.model.Station
import com.federicopeyrani.trainapis.model.Status
import com.federicopeyrani.trainapis.model.Timing
import com.federicopeyrani.trainapis.model.Train
import com.federicopeyrani.trainapis.model.Trip
import com.federicopeyrani.trainapis.providers.Provider
import com.federicopeyrani.trainapis.providers.viaggiatreno.client.api.TrainsApi
import com.federicopeyrani.trainapis.providers.viaggiatreno.client.model.AndamentoTreno
import com.federicopeyrani.trainapis.providers.viaggiatreno.client.model.Fermata
import com.federicopeyrani.trainapis.providers.viaggiatreno.client.model.PartenzeArrivi
import com.federicopeyrani.trainapis.utils.log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.atDate
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.springframework.stereotype.Service
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


/**
 * Client for interacting with the ViaggiaTreno API.
 *
 * This is a wrapper around the auto-generated OpenAPI client that provides
 * a Spring-friendly interface for the ViaggiaTreno API.
 */
@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
@Service
class ViaggiaTrenoClient(
    private val trainsApi: TrainsApi,
) : Provider {

    companion object {

        private val log = log()

        // Format date as 'Tue Jan 20 2026 22:21:07 GMT+0100'
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
            char(' ')
            chars("GMT")
            offset(UtcOffset.Formats.FOUR_DIGITS)
        }

        private val timeFormatter = LocalTime.Format {
            hour()
            chars(":")
            minute()
        }

        private val zone = TimeZone.of("Europe/Rome")
    }

    override fun getDepartures(from: String, date: LocalDateTime) = flow {
        getDeparturesRaw(from, date)
            .asFlow()
            .flatMapMerge(concurrency = 10) { trip ->
                flow {
                    val train = getTrainOrNull(trip)
                    val details = getTripDetails(from = from, trip = trip, train = train)
                    emit(details)
                }
            }
            .collect(this::emit)
    }

    override fun getTrips(from: String, to: String, date: LocalDateTime): Flow<Trip> = flow {
        getDeparturesRaw(from, date)
            .asFlow()
            .flatMapMerge(concurrency = 10) { trip ->
                flow {
                    val train = getTrainOrNull(trip) ?: run {
                        log.warn("Skipping train ${trip.numeroTreno} as train details could not be fetched")
                        return@flow
                    }

                    if (train.fermate?.none { it.id == to } ?: true) {
                        log.debug("Skipping train ${trip.numeroTreno} as it does not stop at $to")
                        return@flow
                    }

                    log.info("Expanding trip details for train ${trip.numeroTreno} from $from to $to")

                    val details = getTripDetails(from = from, to = to, trip = trip, train = train)
                    emit(details)
                }
            }
            .collect(this::emit)
    }

    override suspend fun getRun(runId: String): Run {
        val (trip, trainNumberString, departureTime) = runId.split("_")

        val train = trainsApi
            .andamentoTreno(trip, trainNumberString.toInt(), departureTime.toLong())
            .body()

        return getRunDetails(departureTime.toLong(), train)
    }

    private suspend fun getDeparturesRaw(from: String, date: LocalDateTime): List<PartenzeArrivi> {
        val formattedDate = date
            .toInstant(zone)
            .format(dateFormatter)

        log.info("Fetching departures from ViaggiaTreno for station '$from' at date '$formattedDate'")

        val response = trainsApi.getPartenze(from, formattedDate)

        log.info("Received ${response.body().size} departures from ViaggiaTreno")

        return response.body()
    }

    private suspend fun getTrainOrNull(trip: PartenzeArrivi): AndamentoTreno? =
        runCatching { getTrain(trip) }
            .onFailure { log.warn("Failed to fetch train details for train ${trip.numeroTreno}", it) }
            .getOrNull()

    private suspend fun getTrain(trip: PartenzeArrivi) = trainsApi
        .andamentoTreno(trip.codOrigine, trip.numeroTreno, trip.dataPartenzaTreno)
        .body()

    private suspend fun getTripDetails(
        from: String,
        to: String? = null,
        trip: PartenzeArrivi,
        train: AndamentoTreno?,
    ): Trip {
        val runExpectedArrivalTime = train?.orarioArrivo?.asLocalDateTime()
            ?: trip.orarioArrivo?.asLocalDateTime()
            ?: error("Cannot determine expected arrival time for trip ${trip.numeroTreno}")

        val fromStop = train?.fermate?.firstOrNull { it.id == from }
        val lastStop = train?.fermate?.lastOrNull()
        val destinationStop = if (to != null) train?.fermate?.firstOrNull { it.id == to } else lastStop

        val tripDate = trip.dataPartenzaTreno.asLocalDateTime().date

        val tripDepartureTime = trip.compOrarioPartenza.parseAsTimeAt(tripDate)

        return Trip(
            from = fromStop?.toStation() ?: Station(id = from, name = "Unknown"),
            to = destinationStop?.toStation() ?: Station(
                id = trip.codDestinazione ?: error("Destination code is null for trip ${trip.numeroTreno}"),
                name = trip.destinazione
            ),
            departureTime = Timing(
                scheduled = tripDepartureTime,
                expected = fromStop?.partenzaTeorica?.asLocalDateTime(),
                actual = fromStop?.partenzaReale?.asLocalDateTime(),
            ),
            arrivalTime = Timing(
                scheduled = runExpectedArrivalTime,
                expected = train?.compOrarioArrivoZeroEffettivo?.parseAsTimeAt(tripDate),
                actual = destinationStop?.arrivoReale?.asLocalDateTime(),
            ),
            lastUpdate = Clock.System.now().toLocalDateTime(zone),
            lastUpdateFromProvider = trip.ultimoRilev?.asLocalDateTime(),
            run = train?.let {
                getRunDetails(
                    departureDateAtMidnight = trip.dataPartenzaTreno,
                    train = it,
                )
            }
        )
    }

    private suspend fun getRunDetails(
        departureDateAtMidnight: Long,
        train: AndamentoTreno,
    ): Run {
        val stops = train.fermate
            ?.drop(1)
            ?.dropLast(1)
            ?.map { toIntermediateStop(train, it) }
            ?: error("Train ${train.numeroTreno} has no stops")

        val originStop = train.fermate.first()
        val lastStop = train.fermate.last()

        val tripDate = departureDateAtMidnight.asLocalDateTime().date

        return Run(
            id = "${originStop.id}_${train.numeroTreno}_${departureDateAtMidnight}",
            train = Train(
                id = train.numeroTreno.toString(),
                origin = originStop.toStation(),
                destination = lastStop.toStation(),
            ),
            status = train.provvedimento?.asStatus() ?: Status.ON_TIME,
            origin = OriginStop(
                station = originStop.toStation(),
                departureTime = Timing(
                    scheduled = train.orarioPartenza.asLocalDateTime(),
                    expected = originStop.partenzaTeorica?.asLocalDateTime(),
                    actual = originStop.partenzaReale?.asLocalDateTime(),
                ),
            ),
            destination = DestinationStop(
                station = lastStop.toStation(),
                arrivalTime = Timing(
                    scheduled = train.orarioArrivo.asLocalDateTime(),
                    expected = train.compOrarioArrivoZeroEffettivo?.parseAsTimeAt(tripDate),
                    actual = lastStop.arrivoReale?.asLocalDateTime(),
                ),
            ),
            stops = stops,
            lastUpdate = Clock.System.now().toLocalDateTime(zone),
            lastUpdateFromProvider = train.ultimoRilev?.asLocalDateTime(),
        )
    }

    private fun Int.asStatus() = when (this) {
        1 -> Status.CANCELLED
        2 -> Status.DELAYED
        else -> Status.ON_TIME
    }

    private fun Long.asLocalDateTime() = Instant
        .fromEpochMilliseconds(this)
        .toLocalDateTime(zone)

    private fun String.parseAsTimeAt(date: LocalDate) = timeFormatter
        .parse(this)
        .atDate(date)

    private fun Fermata.toStation() = Station(
        id = this.id,
        name = this.stazione,
    )

    private fun toIntermediateStop(train: AndamentoTreno, stop: Fermata): IntermediateStop {
        // `Fermata.ritardo` will always be 0 if the train has not yet arrived at the stop,
        // so the actual delay from `AndamentoTreno.ritardo` is more accurate
        val delay = train.ritardo?.minutes ?: stop.ritardo?.minutes ?: Duration.ZERO

        return IntermediateStop(
            station = stop.toStation(),
            arrivalTime = Timing(
                scheduled = stop.arrivoTeorico?.asLocalDateTime()
                    ?: error("`arrivoTeorico` is null for stop ${stop.id}"),
                expected = Instant.fromEpochMilliseconds(stop.arrivoTeorico).plus(delay).toLocalDateTime(zone),
                actual = stop.arrivoReale?.asLocalDateTime(),
            ),
            departureTime = Timing(
                scheduled = stop.partenzaTeorica?.asLocalDateTime()
                    ?: error("`partenzaTeorica` is null for stop ${stop.id}"),
                expected = Instant.fromEpochMilliseconds(stop.partenzaTeorica).plus(delay).toLocalDateTime(zone),
                actual = stop.partenzaReale?.asLocalDateTime(),
            ),
        )
    }
}

