package com.federicopeyrani.trainapis.trains

import com.federicopeyrani.trainapis.model.StationDetails
import com.federicopeyrani.trainapis.model.Trip
import com.federicopeyrani.trainapis.providers.StationsProvider
import com.federicopeyrani.trainapis.providers.TripsProvider
import com.federicopeyrani.trainapis.utils.log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapMerge
import org.springframework.stereotype.Service
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Service
@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
class TripsService(
    private val provider: TripsProvider,
    private val stationsProvider: StationsProvider,
) {

    companion object {

        private val log = log()
    }

    fun getStations(query: String): Flow<StationDetails> {
        if (query.isBlank()) return emptyFlow()
        log.info("Getting stations for query '$query'")
        return stationsProvider.getStations(query)
    }

    fun getDepartures(from: String, date: Instant = Clock.System.now()): Flow<Trip> {
        log.info("Getting departures from '$from' at date '$date'")
        return provider.getDepartures(from, date)
    }

    fun getTrips(from: String, to: String, date: Instant = Clock.System.now()): Flow<Trip> {
        log.info("Getting trips from '$from' to '$to' at date '$date'")
        return provider.getTrips(from, to, date)
    }

    fun getAllTrips(from: List<String>, to: List<String>, date: Instant = Clock.System.now()) =
        from.flatMap { f -> to.map { t -> f to t } }
            .asFlow()
            .flatMapMerge(concurrency = 10) { (from, to) -> getTrips(from, to, date) }

    suspend fun getRun(runId: String) = provider.getRun(runId)
}
