package com.federicopeyrani.trainapis.providers.viaggiatreno

import com.federicopeyrani.trainapis.model.DestinationStop
import com.federicopeyrani.trainapis.model.IntermediateStop
import com.federicopeyrani.trainapis.model.OriginStop
import com.federicopeyrani.trainapis.model.Run
import com.federicopeyrani.trainapis.model.Station
import com.federicopeyrani.trainapis.model.Status
import com.federicopeyrani.trainapis.model.StopStatus
import com.federicopeyrani.trainapis.model.Timing
import com.federicopeyrani.trainapis.model.Train
import com.federicopeyrani.trainapis.model.Trip
import com.federicopeyrani.trainapis.providers.TripsProvider
import com.federicopeyrani.trainapis.providers.viaggiatreno.client.model.AndamentoTreno
import com.federicopeyrani.trainapis.providers.viaggiatreno.client.model.Fermata
import com.federicopeyrani.trainapis.providers.viaggiatreno.client.model.PartenzeArrivi
import com.federicopeyrani.trainapis.utils.log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atDate
import kotlinx.datetime.toLocalDateTime
import org.springframework.stereotype.Service
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime


/**
 * Client for interacting with the ViaggiaTreno API.
 *
 * This is a wrapper around the auto-generated OpenAPI client that provides
 * a Spring-friendly interface for the ViaggiaTreno API.
 */
@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
@Service
class ViaggiaTrenoTripsProvider(
    private val trainsApiWrapper: ViaggiaTrenoTrainsApiWrapper,
    private val stationApiWrapper: ViaggiaTrenoStationsApiWrapper,
) : TripsProvider {

    companion object {

        private val log = log()

        private val timeFormatter = LocalTime.Format {
            hour()
            chars(":")
            minute()
        }

        private val zone = TimeZone.of("Europe/Rome")
    }

    override fun getDepartures(from: String, date: Instant) = flow {
        trainsApiWrapper.getDepartures(from, date)
            .asFlow()
            .flatMapMerge(concurrency = 10) { trip ->
                flow {
                    val train = trainsApiWrapper.getTrainOrNull(trip)
                    val details = getTripDetails(from = from, trip = trip, train = train)
                    emit(details)
                }
            }
            .collect(this::emit)
    }

    override fun getTrips(from: String, to: String, date: Instant): Flow<Trip> = flow {
        trainsApiWrapper.getDepartures(from, date)
            .asFlow()
            .flatMapMerge(concurrency = 10) { trip ->
                flow {
                    val train = trainsApiWrapper.getTrainOrNull(trip) ?: run {
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

        val train = trainsApiWrapper.getTrain(trip, trainNumberString.toInt(), departureTime.toLong())

        return getRunDetails(departureTime.toLong(), train)
    }

    private suspend fun getTripDetails(
        from: String,
        to: String? = null,
        trip: PartenzeArrivi,
        train: AndamentoTreno?,
    ): Trip {
        val runExpectedArrivalTime = train?.orarioArrivo?.asLocalDateTime()
            ?: trip.orarioArrivo?.asLocalDateTime()

        val fromStop = train?.fermate?.firstOrNull { it.id == from }
        val lastStop = train?.fermate?.lastOrNull()
        val destinationStop = if (to != null) train?.fermate?.firstOrNull { it.id == to } else lastStop

        val tripDate = trip.dataPartenzaTreno.asLocalDateTime().date

        val tripDepartureTime = trip.compOrarioPartenza.parseAsTimeAt(tripDate)

        return Trip(
            from = fromStop?.toStation() ?: Station(id = from, name = "Unknown"),
            to = destinationStop?.toStation()
            // `codDestinazione` can actually be null for some trips
            // `destinazione` seems to always be non-null though
                ?: getStationByNameOrNull(trip.destinazione)
                ?: error("Destination code is null for trip ${trip.numeroTreno}"),
            departureTime = Timing(
                scheduled = tripDepartureTime,
                expected = fromStop?.partenzaTeorica?.asLocalDateTime(),
                actual = fromStop?.partenzaReale?.asLocalDateTime(),
            ),
            arrivalTime = runExpectedArrivalTime?.let {
                Timing(
                    scheduled = it,
                    expected = train?.compOrarioArrivoZeroEffettivo?.parseAsTimeAt(tripDate),
                    actual = destinationStop?.arrivoReale?.asLocalDateTime(),
                )
            },
            lastUpdate = Clock.System.now().toLocalDateTime(zone), // TODO: use timestamp from cache
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
                status = originStop.getStatus()
            ),
            destination = DestinationStop(
                station = lastStop.toStation(),
                arrivalTime = Timing(
                    scheduled = train.orarioArrivo.asLocalDateTime(),
                    expected = train.compOrarioArrivoZeroEffettivo?.parseAsTimeAt(tripDate),
                    actual = lastStop.arrivoReale?.asLocalDateTime(),
                ),
                status = lastStop.getStatus()
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
            arrivalTime = stop.arrivoTeorico
                ?.asLocalDateTime()
                ?.let {
                    Timing(
                        scheduled = it,
                        expected = Instant.fromEpochMilliseconds(stop.arrivoTeorico).plus(delay).toLocalDateTime(zone),
                        actual = stop.arrivoReale?.asLocalDateTime(),
                    )
                },
            departureTime = stop.partenzaTeorica
                ?.asLocalDateTime()
                ?.let {
                    Timing(
                        scheduled = it,
                        expected = Instant.fromEpochMilliseconds(stop.partenzaTeorica)
                            .plus(delay)
                            .toLocalDateTime(zone),
                        actual = stop.partenzaReale?.asLocalDateTime(),
                    )
                },
            status = stop.getStatus()
        )
    }

    private suspend fun getStationByNameOrNull(name: String): Station? {
        val result = stationApiWrapper.searchStations(name).firstOrNull() ?: return null

        return Station(
            id = result.id,
            name = result.nomeBreve,
        )
    }

    private fun Fermata.getStatus() = when {
        partenzaReale != null -> StopStatus.DEPARTED
        arrivoReale != null -> StopStatus.ARRIVED
        else -> StopStatus.NOT_YET_ARRIVED
    }
}

