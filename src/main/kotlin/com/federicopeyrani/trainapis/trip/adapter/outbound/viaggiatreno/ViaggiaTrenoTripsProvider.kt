package com.federicopeyrani.trainapis.trip.adapter.outbound.viaggiatreno

import com.federicopeyrani.trainapis.shared.domain.Station
import com.federicopeyrani.trainapis.shared.infrastructure.cache.CacheElement
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoStationsApiWrapper
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoProperties
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoTrainsApiWrapper
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.model.AndamentoTreno
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.model.Fermata
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.model.PartenzeArrivi
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.displayName
import com.federicopeyrani.trainapis.shared.util.convertTimeZone
import com.federicopeyrani.trainapis.shared.util.isLastIndex
import com.federicopeyrani.trainapis.shared.util.log
import com.federicopeyrani.trainapis.trip.application.port.outbound.TripsProvider
import com.federicopeyrani.trainapis.trip.domain.DestinationStop
import com.federicopeyrani.trainapis.trip.domain.IntermediateStop
import com.federicopeyrani.trainapis.trip.domain.OriginStop
import com.federicopeyrani.trainapis.trip.domain.Run
import com.federicopeyrani.trainapis.trip.domain.RunStatus
import com.federicopeyrani.trainapis.trip.domain.StopStatus
import com.federicopeyrani.trainapis.trip.domain.Timing
import com.federicopeyrani.trainapis.trip.domain.Train
import com.federicopeyrani.trainapis.trip.domain.Trip
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atDate
import kotlinx.datetime.toLocalDateTime

import org.springframework.stereotype.Component
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Outbound adapter implementing [TripsProvider] on top of the ViaggiaTreno API.
 *
 * Translates ViaggiaTreno responses (Europe/Rome epoch millis) into trip domain models (UTC).
 */
@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
@Component
class ViaggiaTrenoTripsProvider(
    private val trainsApiWrapper: ViaggiaTrenoTrainsApiWrapper,
    private val stationApiWrapper: ViaggiaTrenoStationsApiWrapper,
    private val properties: ViaggiaTrenoProperties,
) : TripsProvider {

    companion object {

        private val log = log()

        private val timeFormatter = LocalTime.Format {
            hour()
            chars(":")
            minute()
        }

        private val providerTimeZone = TimeZone.of("Europe/Rome")
    }

    override fun getDepartures(from: String, date: Instant) = flow {
        val (data, tripLastUpdate) = trainsApiWrapper.getDepartures(from, date)

        data
            .asFlow()
            .flatMapMerge(concurrency = properties.concurrency) { trip ->
                flow {
                    val train = trainsApiWrapper.getTrainOrNull(trip)
                    val details = getTripDetails(
                        from = from,
                        trip = trip,
                        tripLastUpdate = tripLastUpdate,
                        train = train,
                    )
                    emit(details)
                }
            }
            .collect(this::emit)
    }

    override fun getTrips(from: String, to: String, date: Instant): Flow<Trip> = flow {
        val (data, tripLastUpdate) = trainsApiWrapper.getDepartures(from, date)

        data
            .asFlow()
            .flatMapMerge(concurrency = properties.concurrency) { trip ->
                flow {
                    val trainCacheElement = trainsApiWrapper.getTrainOrNull(trip) ?: run {
                        log.warn("Skipping train ${trip.numeroTreno} as train details could not be fetched")
                        return@flow
                    }

                    if (trainCacheElement.data.fermate?.none { it.id == to } ?: true) {
                        log.debug("Skipping train ${trip.numeroTreno} as it does not stop at $to")
                        return@flow
                    }

                    log.info("Expanding trip details for train ${trip.numeroTreno} from $from to $to")

                    val details = getTripDetails(
                        from = from,
                        to = to,
                        trip = trip,
                        tripLastUpdate = tripLastUpdate,
                        train = trainCacheElement,
                    )

                    emit(details)
                }
            }
            .collect(this::emit)
    }

    override suspend fun getRun(runId: String): Run {
        val (trip, trainNumberString, departureTime) = runId.split("_")

        val (train, trainLasUpdate) = trainsApiWrapper.getTrain(trip, trainNumberString.toInt(), departureTime.toLong())

        return getRunDetails(
            departureDateAtMidnight = departureTime.toLong(),
            train = train,
            trainLastUpdate = trainLasUpdate
        )
    }

    private suspend fun getTripDetails(
        from: String,
        to: String? = null,
        trip: PartenzeArrivi,
        tripLastUpdate: Instant,
        train: CacheElement<AndamentoTreno>? = null,
    ): Trip {
        val runExpectedArrivalTime = train?.data?.orarioArrivo?.asUTCDateTime()
            ?: trip.orarioArrivo?.asUTCDateTime()

        val stops = train?.data?.fermate?.withIndex()

        val fromStop = stops?.firstOrNull { (_, stop) -> stop.id == from }

        val destinationStop =
            if (to != null) stops?.firstOrNull { (_, stop) -> stop.id == to }
            else stops?.lastOrNull()

        val tripDate = trip.dataPartenzaTreno.asProviderDateTime().date

        return Trip(
            from = OriginStop(
                station = fromStop?.value
                    ?.toStation()
                    ?: getStationByIdOrNull(from)
                    ?: error("Can't find station details for origin station '$from'"),
                status = when {
                    fromStop == null -> null
                    fromStop.index == 0 -> fromStop.value.getOriginStatus()
                    else -> fromStop.value.getIntermediateStatus()
                },
                departureTime = Timing(
                    scheduled = trip.compOrarioPartenza
                        .parseAsTimeAt(tripDate)
                        .convertTimeZone(providerTimeZone, TimeZone.UTC),
                    expected = fromStop?.value?.partenzaTeorica?.asUTCDateTime(),
                    actual = fromStop?.value?.partenzaReale?.asUTCDateTime(),
                ),
            ),
            to = DestinationStop(
                station = destinationStop?.value?.toStation()
                // `codDestinazione` can actually be null for some trips
                // `destinazione` seems to always be non-null though
                    ?: getStationByNameOrNull(trip.destinazione)
                    ?: error("Destination code is null for trip ${trip.numeroTreno}"),
                status = when {
                    destinationStop == null || train?.data?.fermate == null -> null
                    train.data.fermate.isLastIndex(destinationStop.index) -> destinationStop.value.getDestinationStatus()
                    else -> destinationStop.value.getIntermediateStatus()
                },
                arrivalTime = runExpectedArrivalTime?.let {
                    Timing(
                        scheduled = it,
                        expected = train?.data?.compOrarioArrivoZeroEffettivo
                            ?.parseAsTimeAt(tripDate)
                            ?.convertTimeZone(providerTimeZone, TimeZone.UTC),
                        actual = destinationStop?.value?.arrivoReale?.asUTCDateTime(),
                    )
                },
            ),
            lastUpdate = tripLastUpdate.toLocalDateTime(TimeZone.UTC),
            lastUpdateFromProvider = trip.ultimoRilev?.asUTCDateTime(),
            run = train?.let {
                getRunDetails(
                    departureDateAtMidnight = trip.dataPartenzaTreno,
                    train = it.data,
                    trainLastUpdate = it.timestamp,
                )
            }
        )
    }

    private suspend fun getRunDetails(
        departureDateAtMidnight: Long,
        train: AndamentoTreno,
        trainLastUpdate: Instant,
    ): Run {
        val stops = train.fermate
            ?.drop(1)
            ?.dropLast(1)
            ?.map { it.toIntermediateStop(train) }
            ?: error("Train ${train.numeroTreno} has no stops")

        val originStop = train.fermate.first()
        val lastStop = train.fermate.last()

        val tripDate = departureDateAtMidnight.asProviderDateTime().date

        return Run(
            id = "${originStop.id}_${train.numeroTreno}_${departureDateAtMidnight}",
            train = Train(
                id = train.numeroTreno.toString(),
                origin = originStop.toStation(),
                destination = lastStop.toStation(),
            ),
            status = train.provvedimento?.asStatus() ?: RunStatus.ON_TIME,
            origin = OriginStop(
                station = originStop.toStation(),
                departureTime = Timing(
                    scheduled = train.orarioPartenza.asUTCDateTime(),
                    expected = originStop.partenzaTeorica?.asUTCDateTime(),
                    actual = originStop.partenzaReale?.asUTCDateTime(),
                ),
                status = originStop.getOriginStatus(),
            ),
            destination = DestinationStop(
                station = lastStop.toStation(),
                arrivalTime = Timing(
                    scheduled = train.orarioArrivo.asUTCDateTime(),
                    expected = train.compOrarioArrivoZeroEffettivo
                        ?.parseAsTimeAt(tripDate)
                        ?.convertTimeZone(providerTimeZone, TimeZone.UTC),
                    actual = lastStop.arrivoReale?.asUTCDateTime(),
                ),
                status = lastStop.getDestinationStatus(),
            ),
            stops = stops,
            lastUpdate = trainLastUpdate.toLocalDateTime(TimeZone.UTC),
            lastUpdateFromProvider = train.ultimoRilev?.asUTCDateTime(),
        )
    }

    private fun Int.asStatus() = when (this) {
        1 -> RunStatus.CANCELLED
        2 -> RunStatus.DELAYED
        else -> RunStatus.ON_TIME
    }

    private fun Long.asUTCDateTime() = Instant
        .fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.UTC)

    private fun Long.asProviderDateTime() = Instant
        .fromEpochMilliseconds(this)
        .toLocalDateTime(providerTimeZone)

    private fun String.parseAsTimeAt(date: LocalDate) = timeFormatter
        .parse(this)
        .atDate(date)

    private suspend fun Fermata.toIntermediateStop(train: AndamentoTreno): IntermediateStop {
        // `Fermata.ritardo` will always be 0 if the train has not yet arrived at the stop,
        // so the actual delay from `AndamentoTreno.ritardo` is more accurate
        val delay = train.ritardo?.minutes ?: ritardo?.minutes ?: Duration.ZERO

        return IntermediateStop(
            station = toStation(),
            arrivalTime = arrivoTeorico
                ?.asUTCDateTime()
                ?.let {
                    Timing(
                        scheduled = it,
                        expected = Instant.fromEpochMilliseconds(arrivoTeorico)
                            .plus(delay)
                            .toLocalDateTime(TimeZone.UTC),
                        actual = arrivoReale?.asUTCDateTime(),
                    )
                },
            departureTime = partenzaTeorica
                ?.asUTCDateTime()
                ?.let {
                    Timing(
                        scheduled = it,
                        expected = Instant.fromEpochMilliseconds(partenzaTeorica)
                            .plus(delay)
                            .toLocalDateTime(TimeZone.UTC),
                        actual = partenzaReale?.asUTCDateTime(),
                    )
                },
            status = getIntermediateStatus(),
        )
    }

    private suspend fun Fermata.toStation() = getStationByIdOrNull(id)
        ?: error("Can't find station details for station with id '$id'")

    private suspend fun getStationByNameOrNull(name: String): Station? {
        val result = stationApiWrapper.searchStations(name)
            .data
            .firstOrNull()
            ?: return null

        return getStationByIdOrNull(result.id)
    }

    private suspend fun getStationByIdOrNull(stationId: String): Station? {
        val result = stationApiWrapper.getStationDetails(stationId).data ?: return null
        return Station(
            id = stationId,
            name = result.displayName(),
        )
    }

    private fun Fermata.getIntermediateStatus() = when {
        partenzaReale != null -> StopStatus.DEPARTED
        arrivoReale != null -> StopStatus.ARRIVED
        else -> StopStatus.NOT_YET_ARRIVED
    }

    private fun Fermata.getOriginStatus() = when {
        partenzaReale != null -> StopStatus.DEPARTED
        else -> StopStatus.NOT_YET_DEPARTED
    }

    private fun Fermata.getDestinationStatus() = when {
        arrivoReale != null -> StopStatus.ARRIVED
        else -> StopStatus.NOT_YET_ARRIVED
    }
}

