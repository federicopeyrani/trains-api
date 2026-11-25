package com.federicopeyrani.trainapis.trip.adapter.outbound.viaggiatreno

import com.federicopeyrani.trainapis.shared.domain.Station
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoProperties
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoStationsApiWrapper
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoTrainsApiWrapper
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.model.AndamentoTreno
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.displayName
import com.federicopeyrani.trainapis.trip.adapter.outbound.viaggiatreno.ViaggiaTrenoTripMapper.stationIds
import com.federicopeyrani.trainapis.trip.adapter.outbound.viaggiatreno.ViaggiaTrenoTripMapper.toDeparture
import com.federicopeyrani.trainapis.trip.adapter.outbound.viaggiatreno.ViaggiaTrenoTripMapper.toRun
import com.federicopeyrani.trainapis.trip.application.port.outbound.TripsProvider
import com.federicopeyrani.trainapis.trip.domain.Departure
import com.federicopeyrani.trainapis.trip.domain.Run
import com.federicopeyrani.trainapis.trip.domain.RunId
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

    override fun getDepartures(from: String, date: Instant): Flow<Departure> = flow {
        val (board, boardLastUpdate) = trainsApiWrapper.getDepartures(from, date)
        val station = getStationByIdOrNull(from) ?: error("Can't find station details for origin station '$from'")

        board
            .asFlow()
            .flatMapMerge(concurrency = properties.concurrency) { entry ->
                flow {
                    val run = trainsApiWrapper.getTrainOrNull(entry)?.let { (train, trainLastUpdate) ->
                        train.toRun(entry.dataPartenzaTreno, trainLastUpdate)
                    }
                    val destination = run?.destination?.station
                        // `codDestinazione` can actually be null for some trips
                        // `destinazione` seems to always be non-null though
                        ?: getStationByNameOrNull(entry.destinazione)
                        ?: error("Can't find destination station '${entry.destinazione}' for train ${entry.numeroTreno}")

                    emit(entry.toDeparture(station, destination, boardLastUpdate, run))
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

        return train.toRun(runId.departureDate, trainLastUpdate)
    }

    private suspend fun AndamentoTreno.toRun(departureDate: Long, lastUpdate: Instant) =
        toRun(departureDate, lastUpdate, stations = resolveStations(stationIds()))

    /**
     * Looks up all [ids] concurrently.
     */
    private suspend fun resolveStations(ids: Collection<String>): Map<String, Station> = coroutineScope {
        ids
            .map { id ->
                async {
                    stationLookups.withPermit {
                        getStationByIdOrNull(id) ?: error("Can't find station details for station with id '$id'")
                    }
                }
            }
            .awaitAll()
            .associateBy { it.id }
    }

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
