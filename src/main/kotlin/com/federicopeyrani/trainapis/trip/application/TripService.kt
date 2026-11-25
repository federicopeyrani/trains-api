package com.federicopeyrani.trainapis.trip.application

import com.federicopeyrani.trainapis.shared.util.log
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
import kotlin.time.Instant

@Service
@OptIn(ExperimentalCoroutinesApi::class)
class TripService(
    private val provider: TripsProvider,
) : GetDeparturesUseCase, GetTripsUseCase, GetRunUseCase {

    companion object {

        private val log = log()
    }

    override fun getDepartures(from: String, date: Instant): Flow<Trip> {
        log.info("Getting departures from '$from' at date '$date'")
        return provider.getDepartures(from, date)
    }

    override fun getTrips(from: String, to: String, date: Instant): Flow<Trip> {
        log.info("Getting trips from '$from' to '$to' at date '$date'")
        return provider.getTrips(from, to, date)
    }

    override fun getAllTrips(from: List<String>, to: List<String>, date: Instant) =
        from.flatMap { f -> to.map { t -> f to t } }
            .asFlow()
            .flatMapMerge(concurrency = 10) { (from, to) -> getTrips(from, to, date) }

    override suspend fun getRun(runId: String) = provider.getRun(runId)
}
