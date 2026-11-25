package com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno

import com.federicopeyrani.trainapis.shared.domain.Station
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.model.StazioneDettagliata

/**
 * Title-cases an upper-case ViaggiaTreno station name, keeping separators (e.g. "SESTO S.GIOVANNI" becomes
 * "Sesto S.Giovanni").
 */
fun String.toStationDisplayName() =
    // Using a positive lookbehind to split on special characters, but keeping them in the result to
    // avoid removing them (e.g. "Sesto S.Giovanni" should not become "Sesto S Giovanni")
    //
    // example: Sesto S.Giovanni --> ["Sesto ", "S.", "Giovanni"]
    split(Regex("(?<=\\s|\\.|-)"))
        .joinToString(separator = "") {
            it
                .lowercase()
                .replaceFirstChar { it.titlecase() }
        }

/**
 * Human-friendly station name.
 */
fun StazioneDettagliata.displayName() =
    // `nomeBreve` is usually more user-friendly, but can contain abbreviations (e.g. "Mi.Forlanini").
    //
    // `localita` can actually be `null` if querying with the wrong
    // "codice regione" (e.g. http://www.viaggiatreno.it/infomobilita/resteasy/viaggiatreno/dettaglioStazione/S01322/0)
    // ...or if the API just decides to do so for some reason
    localita
        ?.nomeLungo
        ?.toStationDisplayName()
        ?: codStazione

fun StazioneDettagliata.toStation() = Station(
    id = codStazione,
    name = displayName(),
)
