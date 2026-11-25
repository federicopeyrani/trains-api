package com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno

import kotlin.test.Test
import kotlin.test.assertEquals

class StationMappingTests {

    @Test
    fun `title-cases upper-case names word by word, keeping separators`() {
        mapOf(
            "MILANO CENTRALE" to "Milano Centrale",
            "SESTO S.GIOVANNI" to "Sesto S.Giovanni",
            "VILLA S.GIOVANNI-PORTO" to "Villa S.Giovanni-Porto",
            "PONTE D'ADDA" to "Ponte D'Adda",
        ).forEach { (upstream, displayed) ->
            assertEquals(displayed, upstream.toStationDisplayName())
        }
    }
}
