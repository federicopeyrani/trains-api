package com.federicopeyrani.trainapis.station.adapter.inbound.web

import com.federicopeyrani.trainapis.model.StationDetailsAllOfLocation
import com.federicopeyrani.trainapis.station.domain.GeoLocation
import com.federicopeyrani.trainapis.station.domain.StationDetails
import com.federicopeyrani.trainapis.model.StationDetails as StationDetailsDto

fun StationDetails.toDto() = StationDetailsDto(
    id = id,
    name = name,
    location = location?.toDto(),
)

fun GeoLocation.toDto() = StationDetailsAllOfLocation(
    latitude = latitude.toFloat(),
    longitude = longitude.toFloat(),
)
