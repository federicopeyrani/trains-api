package com.federicopeyrani.trainapis.station.domain

import com.federicopeyrani.trainapis.shared.domain.Station

/**
 * A [station] as returned by a search, with its [location] when the provider knows it.
 */
data class StationDetails(
    val station: Station,
    val location: GeoLocation? = null,
)

data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
)
