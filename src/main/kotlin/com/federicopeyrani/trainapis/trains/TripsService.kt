package com.federicopeyrani.trainapis.trains

import com.federicopeyrani.trainapis.model.StationDetails
import com.federicopeyrani.trainapis.model.Trip
import com.federicopeyrani.trainapis.providers.StationsProvider
import com.federicopeyrani.trainapis.providers.TripsProvider
import com.federicopeyrani.trainapis.utils.log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.springframework.stereotype.Service
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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
        log.info("Getting stations for query '$query'")
        return stationsProvider.getStations(query)
    }

    fun getDepartures(from: String, date: LocalDateTime?): Flow<Trip> {
        val date = date ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        log.info("Getting departures from '$from' at date '$date'")
        return provider.getDepartures(from, date)
    }

    fun getTrips(from: String, to: String, date: LocalDateTime?): Flow<Trip> {
        val date = date ?: getCurrentDateTime()
        log.info("Getting trips from '$from' to '$to' at date '$date'")
        return provider.getTrips(from, to, date)
    }

    fun getAllTrips(from: List<String>, to: List<String>, date: LocalDateTime?) =
        from.flatMap { f -> to.map { t -> f to t } }
            .asFlow()
            .flatMapMerge(concurrency = 10) { (from, to) -> getTrips(from, to, date) }

    suspend fun getRun(runId: String) = provider.getRun(runId)

    private fun getCurrentDateTime(): LocalDateTime =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
}
