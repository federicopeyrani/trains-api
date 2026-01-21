package com.federicopeyrani.trainapis.providers.viaggiatreno

import com.federicopeyrani.trainapis.model.Run
import com.federicopeyrani.trainapis.model.Station
import com.federicopeyrani.trainapis.model.Train
import com.federicopeyrani.trainapis.model.Trip
import com.federicopeyrani.trainapis.providers.Provider
import com.federicopeyrani.trainapis.providers.viaggiatreno.client.api.StationsApi
import com.federicopeyrani.trainapis.providers.viaggiatreno.client.api.TrainsApi
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toInstant
import kotlinx.datetime.toJavaZoneId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant


/**
 * Client for interacting with the ViaggiaTreno API.
 *
 * This is a wrapper around the auto-generated OpenAPI client that provides
 * a Spring-friendly interface for the ViaggiaTreno API.
 */
@Service
class ViaggiaTrenoClient(
    private val trainsApi: TrainsApi,
    private val stationsCache: StationsCache,
) : Provider {

    companion object {

        private val log = LoggerFactory.getLogger(ViaggiaTrenoClient::class.java)

        // Format date as 'Tue Jan 20 2026 22:21:07 GMT+0100
        @OptIn(FormatStringsInDatetimeFormats::class)
        private val dateFormatter = DateTimeComponents.Format {
            dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED)
            chars(" ")
            monthName(MonthNames.ENGLISH_ABBREVIATED)
            chars(" ")
            byUnicodePattern("dd yyyy HH:mm:ss 'GMT'Z")
        }

        private val zone = TimeZone.of("Europe/Rome")
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun getDepartures(from: String, date: LocalDateTime): List<Trip> {
        val formattedDate = date
            .toInstant(zone)
            .format(dateFormatter)

        log.info("Fetching departures from ViaggiaTreno for station '$from' at date '$formattedDate'")

        val response = trainsApi.getPartenze(from, formattedDate)

        log.info("Status code: ${response.status}")
        log.info("Received ${response.body().size} departures from ViaggiaTreno")

        return response.body()
            .take(1)
            .map {
                val train = trainsApi
                    .andamentoTreno(
                        it.codOrigine ?: error("Missing codOrigine"),
                        it.numeroTreno ?: error("Missing numeroTreno"),
                        it.dataPartenzaTreno ?: error("Missing dataPartenzaTreno")
                    )
                    .body()

                val finalArrivalDate = train.orarioArrivo?.toOffsetDateTime() ?: OffsetDateTime.now()

                Trip(
                    from = stationsCache.getStationByName(from) ?: Station(id = from, name = it.origine ?: ""),
                    to = it.destinazione
                        ?.let { stationsCache.getStationByName(it) }
                        ?: error("Station not found: ${it.destinazione}"),
                    departureTime = it.partenzaTreno
                        ?.toOffsetDateTime()
                        ?: OffsetDateTime.now(),
                    arrivalTime = finalArrivalDate,
                    run = Run(
                        id = "${it.numeroTreno}_${it.dataPartenzaTreno}",
                        originDepartureTime = it.orarioPartenza?.toOffsetDateTime()
                            ?: OffsetDateTime.now(),
                        finalArrivalTime = finalArrivalDate,
                        train = Train(
                            id = it.numeroTreno.toString(),
                            origin = train.origine
                                ?.let { stationsCache.getStationByName(it) }
                                ?: error("Station not found: ${train.origine}"),
                            destination = stationsCache.getStationByName(it.destinazione)
                                ?: error("Station not found: ${it.destinazione}"),
                        )
                    )
                )
            }
    }

    private fun Long.toOffsetDateTime(): OffsetDateTime {
        return OffsetDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(this),
            zone.toJavaZoneId()
        )
    }
}

