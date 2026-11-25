package com.federicopeyrani.trainapis.trip.adapter.outbound.viaggiatreno

import com.federicopeyrani.trainapis.shared.domain.Station
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoProperties
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoStationsApiWrapper
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoTrainsApiWrapper
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.model.AndamentoTreno
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.model.Fermata
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.model.PartenzeArrivi
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.displayName
import com.federicopeyrani.trainapis.shared.util.convertTimeZone
import com.federicopeyrani.trainapis.trip.application.port.outbound.TripsProvider
import com.federicopeyrani.trainapis.trip.domain.Departure
import com.federicopeyrani.trainapis.trip.domain.DestinationStop
import com.federicopeyrani.trainapis.trip.domain.IntermediateStop
import com.federicopeyrani.trainapis.trip.domain.OriginStop
import com.federicopeyrani.trainapis.trip.domain.Run
import com.federicopeyrani.trainapis.trip.domain.RunId
import com.federicopeyrani.trainapis.trip.domain.RunStatus
import com.federicopeyrani.trainapis.trip.domain.Timing
import com.federicopeyrani.trainapis.trip.domain.Train
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
import kotlin.time.Instant

/**
 * Outbound adapter implementing [TripsProvider] on top of the ViaggiaTreno API.
 *
 * Translates ViaggiaTreno responses (Europe/Rome epoch millis) into trip domain models (UTC). Decisions about
 * which departures make a trip, and how a trip is derived from a run, belong to the domain.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Component
class ViaggiaTrenoTripsProvider(
    private val trainsApiWrapper: ViaggiaTrenoTrainsApiWrapper,
    private val stationApiWrapper: ViaggiaTrenoStationsApiWrapper,
    private val properties: ViaggiaTrenoProperties,
) : TripsProvider {

    companion object {

        private val timeFormatter = LocalTime.Format {
            hour()
            chars(":")
            minute()
        }

        private val providerTimeZone = TimeZone.of("Europe/Rome")
    }

    override fun getDepartures(from: String, date: Instant): Flow<Departure> = flow {
        val (board, boardLastUpdate) = trainsApiWrapper.getDepartures(from, date)
        val station = getStationByIdOrNull(from) ?: error("Can't find station details for origin station '$from'")

        board
            .asFlow()
            .flatMapMerge(concurrency = properties.concurrency) { entry ->
                flow {
                    val run = trainsApiWrapper.getTrainOrNull(entry)?.let { (train, trainLastUpdate) ->
                        train.toRun(departureDate = entry.dataPartenzaTreno, lastUpdate = trainLastUpdate)
                    }
                    emit(entry.toDeparture(station, boardLastUpdate, run))
                }
            }
            .collect(this::emit)
    }

    override suspend fun getRun(runId: RunId): Run {
        val (train, trainLastUpdate) = trainsApiWrapper.getTrain(
            from = runId.originStationId,
            trainId = runId.trainNumber,
            date = runId.departureDate,
        )

        return train.toRun(departureDate = runId.departureDate, lastUpdate = trainLastUpdate)
    }

    private suspend fun PartenzeArrivi.toDeparture(station: Station, boardLastUpdate: Instant, run: Run?): Departure {
        val tripDate = dataPartenzaTreno.asProviderDateTime().date

        return Departure(
            station = station,
            destination = run?.destination?.station
                // `codDestinazione` can actually be null for some trips
                // `destinazione` seems to always be non-null though
                ?: getStationByNameOrNull(destinazione)
                ?: error("Can't find destination station '$destinazione' for train $numeroTreno"),
            scheduledDeparture = compOrarioPartenza
                .parseAsTimeAt(tripDate)
                .convertTimeZone(providerTimeZone, TimeZone.UTC),
            scheduledArrival = run?.destination?.arrivalTime?.scheduled ?: orarioArrivo?.asUTCDateTime(),
            lastUpdate = boardLastUpdate.toLocalDateTime(TimeZone.UTC),
            lastUpdateFromProvider = ultimoRilev?.asUTCDateTime(),
            run = run,
        )
    }

    private suspend fun AndamentoTreno.toRun(departureDate: Long, lastUpdate: Instant): Run {
        val calls = fermate?.takeIf { it.isNotEmpty() } ?: error("Train $numeroTreno has no stops")
        val first = calls.first()
        val last = calls.last()
        val tripDate = departureDate.asProviderDateTime().date

        return Run(
            id = RunId(originStationId = first.id, trainNumber = numeroTreno, departureDate = departureDate),
            train = Train(
                id = numeroTreno.toString(),
                origin = first.toStation(),
                destination = last.toStation(),
            ),
            status = provvedimento?.asStatus() ?: RunStatus.ON_TIME,
            origin = OriginStop.of(
                station = first.toStation(),
                departureTime = Timing(
                    scheduled = orarioPartenza.asUTCDateTime(),
                    expected = first.partenzaTeorica?.asUTCDateTime(),
                    actual = first.partenzaReale?.asUTCDateTime(),
                ),
            ),
            destination = DestinationStop.of(
                station = last.toStation(),
                arrivalTime = Timing(
                    scheduled = orarioArrivo.asUTCDateTime(),
                    expected = compOrarioArrivoZeroEffettivo
                        ?.parseAsTimeAt(tripDate)
                        ?.convertTimeZone(providerTimeZone, TimeZone.UTC),
                    actual = last.arrivoReale?.asUTCDateTime(),
                ),
            ),
            stops = calls.drop(1).dropLast(1).map { it.toIntermediateStop(trainDelay = ritardo) },
            lastUpdate = lastUpdate.toLocalDateTime(TimeZone.UTC),
            lastUpdateFromProvider = ultimoRilev?.asUTCDateTime(),
        )
    }

    private suspend fun Fermata.toIntermediateStop(trainDelay: Int?): IntermediateStop {
        // `Fermata.ritardo` will always be 0 if the train has not yet arrived at the stop,
        // so the actual delay from `AndamentoTreno.ritardo` is more accurate
        val delay = trainDelay?.minutes ?: ritardo?.minutes ?: Duration.ZERO

        return IntermediateStop.of(
            station = toStation(),
            arrivalTime = arrivoTeorico?.let { Timing.delayed(it.asUTCDateTime(), delay, arrivoReale?.asUTCDateTime()) },
            departureTime = partenzaTeorica?.let {
                Timing.delayed(it.asUTCDateTime(), delay, partenzaReale?.asUTCDateTime())
            },
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
}
