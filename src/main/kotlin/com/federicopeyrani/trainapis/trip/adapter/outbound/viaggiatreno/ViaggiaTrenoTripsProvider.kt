package com.federicopeyrani.trainapis.trip.adapter.outbound.viaggiatreno

import com.federicopeyrani.trainapis.shared.domain.Station
import com.federicopeyrani.trainapis.shared.domain.StationId
import com.federicopeyrani.trainapis.shared.domain.StationNotFoundException
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoProperties
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoStationsApiWrapper
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoTrainsApiWrapper
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.model.AndamentoTreno
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.model.PartenzeArrivi
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.displayName
import com.federicopeyrani.trainapis.shared.util.log
import com.federicopeyrani.trainapis.trip.adapter.outbound.viaggiatreno.ViaggiaTrenoTripMapper.stationIds
import com.federicopeyrani.trainapis.trip.adapter.outbound.viaggiatreno.ViaggiaTrenoTripMapper.toDeparture
import com.federicopeyrani.trainapis.trip.adapter.outbound.viaggiatreno.ViaggiaTrenoTripMapper.toRun
import com.federicopeyrani.trainapis.trip.application.port.outbound.TripsProvider
import com.federicopeyrani.trainapis.trip.domain.Departure
import com.federicopeyrani.trainapis.trip.domain.Run
import com.federicopeyrani.trainapis.trip.domain.RunId
import com.federicopeyrani.trainapis.trip.domain.RunNotFoundException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.springframework.stereotype.Component
import kotlin.time.Instant

/**
 * Outbound adapter implementing [TripsProvider] on top of the ViaggiaTreno API.
 *
 * Fetches boards and trains, resolves the stations they reference, and hands everything to the pure
 * [ViaggiaTrenoTripMapper]. Decisions about which departures make a trip belong to the domain.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Component
class ViaggiaTrenoTripsProvider(
    private val trainsApiWrapper: ViaggiaTrenoTrainsApiWrapper,
    private val stationApiWrapper: ViaggiaTrenoStationsApiWrapper,
    private val properties: ViaggiaTrenoProperties,
) : TripsProvider {

    /**
     * Bounds concurrent station lookups across all requests, on top of the per-board train fan-out.
     */
    private val stationLookups = Semaphore(properties.concurrency)

    private val log = log()

    override fun getDepartures(from: StationId, date: Instant): Flow<Departure> = flow {
        val station = getStationByIdOrNull(from) ?: throw StationNotFoundException(from)
        val (board, boardLastUpdate) = trainsApiWrapper.getDepartures(from.value, date)

        board
            .asFlow()
            .flatMapMerge(concurrency = properties.concurrency) { entry ->
                flow { emit(entry.resolveDeparture(station, boardLastUpdate)) }
            }
            .collect(this::emit)
    }

    override suspend fun getRun(runId: RunId): Run {
        val (train, trainLastUpdate) = trainsApiWrapper.getTrain(
            from = runId.originStationId.value,
            trainId = runId.trainNumber.value,
            date = runId.departureDate,
        )

        return train?.toRun(runId.departureDate, trainLastUpdate) ?: throw RunNotFoundException(runId)
    }

    /**
     * Best effort: a departure whose train cannot be fetched is returned without its run, and one whose destination
     * cannot be resolved either is returned without destination, so a single broken entry never fails the whole
     * (already streaming) board, and callers can still tell that it exists.
     */
    private suspend fun PartenzeArrivi.resolveDeparture(station: Station, boardLastUpdate: Instant): Departure {
        val run = orNull("run of train $numeroTreno") {
            trainsApiWrapper.getTrainOrNull(this)?.let { (train, trainLastUpdate) ->
                train.toRun(dataPartenzaTreno, trainLastUpdate)
            }
        }

        val destination = run?.destination?.station
            // Board entries have no usable destination id (`codDestinazione` is always null), but `destinazione` is the
            // exact `nomeLungo` of the destination station
            ?: orNull("destination '$destinazione' of train $numeroTreno") { getStationByNameOrNull(destinazione) }

        return toDeparture(station, destination, boardLastUpdate, run)
    }

    private suspend fun AndamentoTreno.toRun(departureDate: Long, lastUpdate: Instant) =
        toRun(departureDate, lastUpdate, stations = resolveStations(stationIds()))

    /**
     * Looks up all [ids] concurrently. Stations that cannot be resolved are left out (the mapper then falls back to
     * the stop's own name).
     */
    private suspend fun resolveStations(ids: Collection<StationId>): Map<StationId, Station> = coroutineScope {
        ids
            .map { id -> async { stationLookups.withPermit { orNull("station '$id'") { getStationByIdOrNull(id) } } } }
            .awaitAll()
            .filterNotNull()
            .associateBy { it.id }
    }

    private suspend fun <T> orNull(what: String, block: suspend () -> T?): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Could not resolve $what: ${e.message}")
            null
        }

    /**
     * The station whose name is exactly [name]. `cercaStazione` matches by prefix, so its first result may be another
     * station (searching "VERONA" also lists "VERONA PORTA NUOVA"); without an exact match the station is unknown.
     */
    private suspend fun getStationByNameOrNull(name: String): Station? {
        val result = stationApiWrapper.searchStations(name)
            .data
            .singleOrNull { it.nomeLungo.equals(name.trim(), ignoreCase = true) }
            ?: return null

        return getStationByIdOrNull(StationId(result.id))
    }

    private suspend fun getStationByIdOrNull(stationId: StationId): Station? {
        val result = stationApiWrapper.getStationDetails(stationId.value) ?: return null

        return Station(
            id = stationId,
            name = result.displayName(),
        )
    }
}
