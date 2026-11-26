package com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno

import com.federicopeyrani.trainapis.shared.infrastructure.cache.Cache
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.api.StationsApi
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.model.StazioneDettagliata
import com.federicopeyrani.trainapis.shared.util.log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.toKotlinDuration

/**
 * Every station of ViaggiaTreno's per-region lists (`elencoStazioni/0..22`), loaded at once and kept for a day, so that
 * resolving a station costs no upstream call.
 *
 * The lists are not complete everywhere: no single region (0 included) covers every station, but all of them together
 * covered every station seen on boards and runs. Entries are complete except for a few stations without coordinates.
 * Callers fall back to a direct lookup when a station is not [found][find].
 *
 * @param regions the lists to load; all ViaggiaTreno regions by default
 * @param ttls how long the lists, or a failure to load them, are kept
 */
class ViaggiaTrenoStationRegistry(
    private val stationsApi: StationsApi,
    private val cache: Cache,
    private val regions: List<Int> = (0..22).toList(),
    private val ttls: ViaggiaTrenoProperties.CacheTtls = ViaggiaTrenoProperties.CacheTtls(),
) {

    companion object {

        private val log = log()

        /**
         * Lists loaded at the same time, to avoid a burst of upstream calls.
         */
        private const val CONCURRENT_LOADS = 4
    }

    /**
     * Station [id] with its name and coordinates, or `null` when it is not listed with both, or the lists cannot be
     * loaded right now.
     */
    suspend fun find(id: String): StazioneDettagliata? = stations()?.get(id)

    /**
     * All listed stations by id, or `null` if the lists could not be loaded. A failure is remembered for a while,
     * so an outage does not cost a full reload on every lookup.
     */
    private suspend fun stations() = cache.getOrPut(
        key = Cache.Key.of<Map<String, StazioneDettagliata>?>("station-registry"),
        ttl = {
            if (it == null) ttls.stationRegistryFailure.toKotlinDuration() else ttls.stationRegistry.toKotlinDuration()
        },
    ) {
        try {
            load()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Station lists unavailable, looking stations up one by one: ${e.message}")
            null
        }
    }.data

    private suspend fun load(): Map<String, StazioneDettagliata> = coroutineScope {
        log.info("Loading the station lists of regions $regions from ViaggiaTreno")
        val permits = Semaphore(CONCURRENT_LOADS)
        regions
            .map { region ->
                async {
                    permits.withPermit {
                        viaggiaTrenoCall("station list") { stationsApi.elencoStazioni(region).bodyOrThrow("station list") }
                    }
                }
            }
            .awaitAll()
            .flatten()
            // Border stations are listed in several regions with the same data
            .filter { it.localita != null && it.hasCoordinates() }
            .associateBy { it.codStazione }
            .also { log.info("Loaded ${it.size} stations") }
    }

    private fun StazioneDettagliata.hasCoordinates() = lat != null && lon != null && lat != 0.0 && lon != 0.0
}
