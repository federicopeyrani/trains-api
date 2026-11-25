package com.federicopeyrani.trainapis.trip.application

import com.federicopeyrani.trainapis.shared.util.log
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetAllTripsUseCase
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetDeparturesUseCase
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetRunUseCase
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetTripsUseCase
import com.federicopeyrani.trainapis.trip.application.port.outbound.TripsProvider
import com.federicopeyrani.trainapis.trip.domain.RunId
import com.federicopeyrani.trainapis.trip.domain.Trip
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
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

    override fun getDepartures(from: String, date: Instant?): Flow<Trip> {
        val at = date ?: clock.now()
        log.info("Getting departures from '$from' at date '$at'")
        return provider.getDepartures(from, at).map { it.toTrip() }
    }

    override fun getTrips(from: String, to: String, date: Instant?): Flow<Trip> {
        val at = date ?: clock.now()
        log.info("Getting trips from '$from' to '$to' at date '$at'")
        return provider.getDepartures(from, at)
            .filter { it.serves(to) }
            .map { it.toTrip(to) }
    }

    /**
     * Each origin's departure board is fetched once and matched against every destination.
     */
    override fun getAllTrips(from: List<String>, to: List<String>, date: Instant?): Flow<Trip> {
        val at = date ?: clock.now()
        val destinations = to.distinct()
        log.info("Getting all trips from $from to $destinations at date '$at'")
        return from.distinct()
            .asFlow()
            .flatMapMerge { origin ->
                provider.getDepartures(origin, at).transform { departure ->
                    destinations
                        .filter { departure.serves(it) }
                        .forEach { emit(departure.toTrip(it)) }
                }
            }
    }

    override suspend fun getRun(runId: String) = provider.getRun(RunId.parse(runId))
}
