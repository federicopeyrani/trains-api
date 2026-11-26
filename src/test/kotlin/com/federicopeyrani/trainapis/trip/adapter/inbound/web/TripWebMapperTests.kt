package com.federicopeyrani.trainapis.trip.adapter.inbound.web

import com.federicopeyrani.trainapis.shared.infrastructure.web.ApiJson
import com.federicopeyrani.trainapis.trip.domain.Tracking
import com.federicopeyrani.trainapis.trip.domain.TripFixtures
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.time.Instant

class TripWebMapperTests {

    @Test
    fun `writes every tracking state, with the last detection only when there is one`() {
        val detection = Instant.parse("2026-01-23T08:30:00Z")
        val detected = ""","lastDetection":"2026-01-23T08:30:00Z"}"""
        val cases = mapOf(
            Tracking.Live(detection) to """"tracking":{"state":"LIVE"$detected""",
            Tracking.Stale(detection) to """"tracking":{"state":"STALE"$detected""",
            Tracking.Pending to """"tracking":{"state":"PENDING"}""",
            Tracking.Missing to """"tracking":{"state":"MISSING"}""",
            Tracking.NotTracked(detection) to """"tracking":{"state":"NOT_TRACKED"$detected""",
            Tracking.NotTracked() to """"tracking":{"state":"NOT_TRACKED"}""",
        )

        cases.forEach { (tracking, json) ->
            assertContains(ApiJson.encodeToString(TripFixtures.run("A", "B").copy(tracking = tracking).toDto()), json)
        }
    }
}
