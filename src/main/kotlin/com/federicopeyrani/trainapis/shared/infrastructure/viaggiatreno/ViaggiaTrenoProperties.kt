package com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * @property concurrency maximum number of concurrent upstream calls when fanning out per departure/station
 * @property cache how long each kind of upstream response is cached
 */
@ConfigurationProperties("providers.viaggiatreno")
data class ViaggiaTrenoProperties(
    val concurrency: Int = 10,
    val cache: CacheTtls = CacheTtls(),
) {

    /**
     * Time to live of the cached ViaggiaTreno responses (`providers.viaggiatreno.cache.*`, e.g. `10m`, `24h`).
     *
     * @property stationSearch station search results
     * @property stationDetails details of a station looked up directly (not in the station lists)
     * @property stationDetailsMissing a direct lookup that found no details (unknown station, placeholder)
     * @property stationRegion the region of a station, needed for a direct lookup
     * @property stationRegistry the per-region station lists
     * @property stationRegistryFailure the station lists failing to load, before retrying
     * @property departures a departure board (per station and minute)
     * @property train the status of a train run
     * @property trainUnknown a train run that ViaggiaTreno does not know (yet)
     */
    data class CacheTtls(
        val stationSearch: Duration = Duration.ofMinutes(60),
        val stationDetails: Duration = Duration.ofMinutes(60),
        val stationDetailsMissing: Duration = Duration.ofSeconds(10),
        val stationRegion: Duration = Duration.ofMinutes(60),
        val stationRegistry: Duration = Duration.ofHours(24),
        val stationRegistryFailure: Duration = Duration.ofMinutes(5),
        val departures: Duration = Duration.ofMinutes(10),
        val train: Duration = Duration.ofMinutes(10),
        val trainUnknown: Duration = Duration.ofMinutes(1),
    )
}
