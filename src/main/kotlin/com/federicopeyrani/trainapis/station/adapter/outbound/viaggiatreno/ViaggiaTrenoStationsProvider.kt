package com.federicopeyrani.trainapis.station.adapter.outbound.viaggiatreno


import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoProperties
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.ViaggiaTrenoStationsApiWrapper
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.model.StazioneDettagliata
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.toStation
import com.federicopeyrani.trainapis.shared.util.log
import com.federicopeyrani.trainapis.station.application.port.outbound.StationsProvider
import com.federicopeyrani.trainapis.station.domain.GeoLocation
import com.federicopeyrani.trainapis.station.domain.StationDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import org.springframework.stereotype.Component

@OptIn(ExperimentalCoroutinesApi::class)
@Component
class ViaggiaTrenoStationsProvider(
    private val viaggiaTrenoStationsApiWrapper: ViaggiaTrenoStationsApiWrapper,
    private val properties: ViaggiaTrenoProperties,
) : StationsProvider {

    private val log = log()

    override fun getStations(query: String) = flow {
        viaggiaTrenoStationsApiWrapper.searchStations(query)
            .data
            .asFlow()
            .flatMapMerge(concurrency = properties.concurrency) {
                flow {
                    // Best effort: a station whose details cannot be fetched is skipped rather than failing the
                    // whole (already streaming) result
                    val details = try {
                        viaggiaTrenoStationsApiWrapper.getStationDetails(it.id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log.warn("Skipping station ${it.id}: ${e.message}")
                        null
                    }
                    details?.toStationDetails()?.let { emit(it) }
                }
            }
            .collect { emit(it) }
    }

    private fun StazioneDettagliata.toStationDetails() = StationDetails(
        station = toStation(),
        location = if (lat != null && lon != null) GeoLocation(latitude = lat, longitude = lon) else null,
    )
}
