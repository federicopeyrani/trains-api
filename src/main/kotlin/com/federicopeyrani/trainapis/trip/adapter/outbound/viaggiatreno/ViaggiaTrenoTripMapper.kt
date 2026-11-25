package com.federicopeyrani.trainapis.trip.adapter.outbound.viaggiatreno

import com.federicopeyrani.trainapis.shared.domain.Station
import com.federicopeyrani.trainapis.shared.domain.StationId
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.model.AndamentoTreno
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.model.Fermata
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.model.PartenzeArrivi
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.toStationDisplayName
import com.federicopeyrani.trainapis.trip.domain.Departure
import com.federicopeyrani.trainapis.trip.domain.DestinationStop
import com.federicopeyrani.trainapis.trip.domain.IntermediateStop
import com.federicopeyrani.trainapis.trip.domain.OriginStop
import com.federicopeyrani.trainapis.trip.domain.Run
import com.federicopeyrani.trainapis.trip.domain.RunId
import com.federicopeyrani.trainapis.trip.domain.RunStatus
import com.federicopeyrani.trainapis.trip.domain.Timing
import com.federicopeyrani.trainapis.trip.domain.Train
import com.federicopeyrani.trainapis.trip.domain.TrainNumber
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Pure translation of ViaggiaTreno payloads into trip domain models.
 *
 * Station details are not part of the payloads, so callers resolve them beforehand and pass them in; stops whose
 * station could not be resolved fall back to the name carried by the stop itself.
 * ViaggiaTreno times are epoch millis. Its pre-formatted `HH:mm` fields (`comp*`) are not used: they can disagree
 * with the raw times, and they carry no date.
 */
object ViaggiaTrenoTripMapper {

    /**
     * Ids of every station this train calls at.
     */
    fun AndamentoTreno.stationIds() = fermate.orEmpty().map { StationId(it.id) }.distinct()

    /**
     * @param departureDate epoch millis of the run's departure day at midnight (provider time)
     * @param stations resolved stations by id (normally every id in [stationIds])
     */
    fun AndamentoTreno.toRun(departureDate: Long, lastUpdate: Instant, stations: Map<StationId, Station>): Run {
        val calls = fermate?.takeIf { it.isNotEmpty() } ?: error("Train $numeroTreno has no stops")
        val first = calls.first()
        val last = calls.last()

        fun Fermata.station() = StationId(id).let { stations[it] ?: Station(id = it, name = stazione.toStationDisplayName()) }

        return Run(
            id = RunId(originStationId = StationId(first.id), trainNumber = TrainNumber(numeroTreno), departureDate = departureDate),
            train = Train(
                number = TrainNumber(numeroTreno),
                origin = first.station(),
                destination = last.station(),
            ),
            status = status(),
            origin = OriginStop.of(
                station = first.station(),
                departureTime = timing(first.partenzaTeorica ?: orarioPartenza, first.partenzaReale, ritardo),
            ),
            destination = DestinationStop.of(
                station = last.station(),
                arrivalTime = timing(last.arrivoTeorico ?: orarioArrivo, last.arrivoReale, ritardo),
            ),
            stops = calls.drop(1).dropLast(1).map { it.toIntermediateStop(it.station(), trainDelay = ritardo) },
            lastUpdate = lastUpdate,
            lastUpdateFromProvider = ultimoRilev?.asInstant(),
        )
    }

    /**
     * @param station the station whose board this entry belongs to
     * @param destination the train's final destination, if known
     */
    fun PartenzeArrivi.toDeparture(
        station: Station,
        destination: Station?,
        boardLastUpdate: Instant,
        run: Run?,
    ) = Departure(
        station = station,
        destination = destination,
        // `orarioPartenza` is the departure from this station as an instant, so unlike `compOrarioPartenza` ("HH:mm")
        // it needs no date, which would be wrong for trains reaching this station after midnight
        scheduledDeparture = orarioPartenza.asInstant(),
        scheduledArrival = run?.destination?.arrivalTime?.scheduled ?: orarioArrivo?.asInstant(),
        lastUpdate = boardLastUpdate,
        lastUpdateFromProvider = ultimoRilev?.asInstant(),
        run = run,
    )

    private fun Fermata.toIntermediateStop(station: Station, trainDelay: Int?) = IntermediateStop.of(
        station = station,
        arrivalTime = arrivoTeorico?.let { timing(it, arrivoReale, trainDelay) },
        departureTime = partenzaTeorica?.let { timing(it, partenzaReale, trainDelay) },
    )

    /**
     * Times of an event at a stop, as the ViaggiaTreno site shows them: [scheduled] from the stop itself, and until the
     * event happens, a forecast of [scheduled] plus the train's current delay. The stop's own `ritardo` only describes
     * events that already happened, so it is not used for forecasts.
     */
    private fun timing(scheduled: Long, actual: Long?, trainDelay: Int?) = Timing.delayed(
        scheduled = scheduled.asInstant(),
        delay = trainDelay?.minutes,
        actual = actual?.asInstant(),
    )

    /**
     * A cancelled train is `tipoTreno` `ST` with `provvedimento` 1 (it still comes with its full stop list); otherwise
     * the train is delayed whenever `ritardo` is positive, as the ViaggiaTreno site reports it. `ritardo` is null before
     * departure and negative when running early. Other `provvedimento` codes (partial cancellation, diversion) have
     * never been observed, so they do not change the status.
     */
    private fun AndamentoTreno.status() = when {
        provvedimento == 1 || tipoTreno == AndamentoTreno.TipoTreno.ST -> RunStatus.CANCELLED
        (ritardo ?: 0) > 0 -> RunStatus.DELAYED
        else -> RunStatus.ON_TIME
    }

    private fun Long.asInstant() = Instant.fromEpochMilliseconds(this)

}
