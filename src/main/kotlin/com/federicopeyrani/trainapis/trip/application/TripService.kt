package com.federicopeyrani.trainapis.trip.application

import com.federicopeyrani.trainapis.shared.util.log
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetAllTripsUseCase
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetDeparturesUseCase
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetRunUseCase
import com.federicopeyrani.trainapis.trip.application.port.inbound.GetTripsUseCase
import com.federicopeyrani.trainapis.trip.application.port.outbound.TripsProvider
import com.federicopeyrani.trainapis.trip.domain.Trip
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
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
        return provider.getDepartures(from, at)
    }

    override fun getTrips(from: String, to: String, date: Instant?): Flow<Trip> {
        val at = date ?: clock.now()
        log.info("Getting trips from '$from' to '$to' at date '$at'")
        return provider.getTrips(from, to, at)
    }

    override fun getAllTrips(from: List<String>, to: List<String>, date: Instant?): Flow<Trip> {
        val at = date ?: clock.now()
        return from.flatMap { f -> to.map { t -> f to t } }
            .asFlow()
            .flatMapMerge(concurrency = 10) { (from, to) -> getTrips(from, to, at) }
    }

    override suspend fun getRun(runId: String) = provider.getRun(runId)
}
