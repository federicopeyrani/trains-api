package com.federicopeyrani.trainapis.station.domain

data class StationDetails(
    val id: String,
    val name: String,
    val location: GeoLocation? = null,
)

data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
)
