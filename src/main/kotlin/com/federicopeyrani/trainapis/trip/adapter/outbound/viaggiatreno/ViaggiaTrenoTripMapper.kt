package com.federicopeyrani.trainapis.trip.adapter.outbound.viaggiatreno

import com.federicopeyrani.trainapis.shared.domain.Station
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.model.AndamentoTreno
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.model.Fermata
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.model.PartenzeArrivi
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.toStationDisplayName
import com.federicopeyrani.trainapis.shared.util.convertTimeZone
import com.federicopeyrani.trainapis.trip.domain.Departure
import com.federicopeyrani.trainapis.trip.domain.DestinationStop
import com.federicopeyrani.trainapis.trip.domain.IntermediateStop
import com.federicopeyrani.trainapis.trip.domain.OriginStop
import com.federicopeyrani.trainapis.trip.domain.Run
import com.federicopeyrani.trainapis.trip.domain.RunId
import com.federicopeyrani.trainapis.trip.domain.RunStatus
import com.federicopeyrani.trainapis.trip.domain.Timing
import com.federicopeyrani.trainapis.trip.domain.Train
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Pure translation of ViaggiaTreno payloads into trip domain models.
 *
 * Station details are not part of the payloads, so callers resolve them beforehand and pass them in; stops whose
 * station could not be resolved fall back to the name carried by the stop itself.
 * ViaggiaTreno times are Europe/Rome; domain times are UTC.
 */
object ViaggiaTrenoTripMapper {

    private val timeFormatter = LocalTime.Format {
        hour()
        chars(":")
        minute()
    }

    private val providerTimeZone = TimeZone.of("Europe/Rome")

    /**
     * Ids of every station this train calls at.
     */
    fun AndamentoTreno.stationIds() = fermate.orEmpty().map { it.id }.distinct()

    /**
     * @param departureDate epoch millis of the run's departure day at midnight (provider time)
     * @param stations resolved stations by id (normally every id in [stationIds])
     */
    fun AndamentoTreno.toRun(departureDate: Long, lastUpdate: Instant, stations: Map<String, Station>): Run {
        val calls = fermate?.takeIf { it.isNotEmpty() } ?: error("Train $numeroTreno has no stops")
        val first = calls.first()
        val last = calls.last()

        fun Fermata.station() = stations[id] ?: Station(id = id, name = stazione.toStationDisplayName())

        return Run(
            id = RunId(originStationId = first.id, trainNumber = numeroTreno, departureDate = departureDate),
            train = Train(
                id = numeroTreno.toString(),
                origin = first.station(),
                destination = last.station(),
            ),
            status = provvedimento?.asStatus() ?: RunStatus.ON_TIME,
            origin = OriginStop.of(
                station = first.station(),
                departureTime = Timing(
                    scheduled = orarioPartenza.asUTCDateTime(),
                    expected = first.partenzaTeorica?.asUTCDateTime(),
                    actual = first.partenzaReale?.asUTCDateTime(),
                ),
            ),
            destination = DestinationStop.of(
                station = last.station(),
                arrivalTime = Timing(
                    scheduled = orarioArrivo.asUTCDateTime(),
                    expected = compOrarioArrivoZeroEffettivo
                        ?.parseAsTimeNear(orarioArrivo)
                        ?.convertTimeZone(providerTimeZone, TimeZone.UTC),
                    actual = last.arrivoReale?.asUTCDateTime(),
                ),
            ),
            stops = calls.drop(1).dropLast(1).map { it.toIntermediateStop(it.station(), trainDelay = ritardo) },
            lastUpdate = lastUpdate.toLocalDateTime(TimeZone.UTC),
            lastUpdateFromProvider = ultimoRilev?.asUTCDateTime(),
        )
    }

    /**
     * @param station the station whose board this entry belongs to
     * @param destination the train's final destination
     */
    fun PartenzeArrivi.toDeparture(
        station: Station,
        destination: Station,
        boardLastUpdate: Instant,
        run: Run?,
    ) = Departure(
        station = station,
        destination = destination,
        // `orarioPartenza` is the departure from this station as an instant, so unlike `compOrarioPartenza` ("HH:mm")
        // it needs no date, which would be wrong for trains reaching this station after midnight
        scheduledDeparture = orarioPartenza.asUTCDateTime(),
        scheduledArrival = run?.destination?.arrivalTime?.scheduled ?: orarioArrivo?.asUTCDateTime(),
        lastUpdate = boardLastUpdate.toLocalDateTime(TimeZone.UTC),
        lastUpdateFromProvider = ultimoRilev?.asUTCDateTime(),
        run = run,
    )

    private fun Fermata.toIntermediateStop(station: Station, trainDelay: Int?): IntermediateStop {
        // `Fermata.ritardo` will always be 0 if the train has not yet arrived at the stop,
        // so the actual delay from `AndamentoTreno.ritardo` is more accurate
        val delay = trainDelay?.minutes ?: ritardo?.minutes ?: Duration.ZERO

        return IntermediateStop.of(
            station = station,
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

    /**
     * Parses an `HH:mm` provider time as the occurrence closest to [reference] (epoch millis), so that times past
     * midnight land on the right day.
     */
    private fun String.parseAsTimeNear(reference: Long): LocalDateTime {
        val time = timeFormatter.parse(this)
        val referenceDateTime = reference.asProviderDateTime()
        val referenceInstant = referenceDateTime.toInstant(providerTimeZone)

        return listOf(-1, 0, 1)
            .map { referenceDateTime.date.plus(it, DateTimeUnit.DAY).atTime(time) }
            .minBy { (it.toInstant(providerTimeZone) - referenceInstant).absoluteValue }
    }
}
