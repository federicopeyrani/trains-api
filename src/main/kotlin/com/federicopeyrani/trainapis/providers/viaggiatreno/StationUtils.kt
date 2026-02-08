package com.federicopeyrani.trainapis.providers.viaggiatreno

import com.federicopeyrani.trainapis.model.StationDetails
import com.federicopeyrani.trainapis.model.StationDetailsAllOfLocation
import com.federicopeyrani.trainapis.providers.viaggiatreno.client.model.StazioneDettagliata

fun StazioneDettagliata.toStation() = StationDetails(
    id = codStazione,
    // `nomeBreve` is usually more user-friendly, but can contain abbreviations (e.g. "Mi.Forlanini").
    //
    // `localita` can actually be `null` if querying with the wrong
    // "codice regione" (e.g. http://www.viaggiatreno.it/infomobilita/resteasy/viaggiatreno/dettaglioStazione/S01322/0)
    // ...or if the API just decides to do so for some reason
    name = localita
        ?.nomeLungo
        // Using a positive lookbehind to split on special characters, but keeping them in the result to
        // avoid removing them (e.g. "Sesto S.Giovanni" should not become "Sesto S Giovanni")
        //
        // example: Sesto S.Giovanni --> ["Sesto ", "S.", "Giovanni"]
        ?.split(Regex("(?<=\\s|\\.|-)"))
        ?.joinToString(separator = "") {
            it
                .lowercase()
                .replaceFirstChar { it.titlecase() }
        }
        ?: codStazione,
    location = if (lat != null && lon != null)
        StationDetailsAllOfLocation(
            latitude = lat.toFloat(),
            longitude = lon.toFloat(),
        ) else null
)
