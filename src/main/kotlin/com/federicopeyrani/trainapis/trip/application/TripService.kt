package com.federicopeyrani.trainapis.trip.application

import com.federicopeyrani.trainapis.shared.domain.StationId
import com.federicopeyrani.trainapis.shared.util.log
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetAllTripsUseCase
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetDeparturesUseCase
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetRunUseCase
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetTripsUseCase
import com.federicopeyrani.trainapis.trip.application.port.inbound.TripSearchResult
import com.federicopeyrani.trainapis.trip.application.port.outbound.TripsProvider
import com.federicopeyrani.trainapis.trip.domain.RunId
import com.federicopeyrani.trainapis.trip.domain.Serves
import com.federicopeyrani.trainapis.trip.domain.Trip
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.transform
import org.springframework.stereotype.Service
import kotlin.time.Clock
import kotlin.time.Instant

@Service
@OptIn(ExperimentalCoroutinesApi::class)
class TripService(
    private val provider: TripsProvider,
    private val clock: Clock,
) : GetDeparturesUseCase, GetTripsUseCase, GetAllTripsUseCase, GetRunUseCase {

    companion object {

        private val log = log()
    }

    /**
     * Departures whose destination is unknown are left out.
     */
    override fun getDepartures(from: StationId, date: Instant?): Flow<Trip> {
        val at = date ?: clock.now()
        log.info("Getting departures from '$from' at date '$at'")
        return provider.getDepartures(from, at).mapNotNull { it.toTrip() }
    }

    override fun getTrips(from: StationId, to: StationId, date: Instant?): Flow<TripSearchResult> =
        getAllTrips(listOf(from), listOf(to), date)

    /**
     * Each origin's departure board is fetched once and matched against every destination.
     */
    override fun getAllTrips(from: List<StationId>, to: List<StationId>, date: Instant?): Flow<TripSearchResult> {
        val at = date ?: clock.now()
        val destinations = to.distinct()
        log.info("Getting trips from $from to $destinations at date '$at'")
        return from.distinct()
            .asFlow()
            .flatMapMerge { origin ->
                provider.getDepartures(origin, at).transform { departure ->
                    val serves = destinations.associateWith { departure.serves(it) }
                    if (Serves.UNKNOWN in serves.values) emit(TripSearchResult.Unverified(departure))
                    serves
                        .filterValues { it == Serves.YES }
                        .forEach { (destination, _) -> emit(TripSearchResult.Match(departure.toTrip(destination))) }
                }
            }
    }

    override suspend fun getRun(runId: String) = provider.getRun(RunId.parse(runId))
}
