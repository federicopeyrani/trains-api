package com.federicopeyrani.trainapis.shared.infrastructure.web

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.beans.PropertyEditorSupport
import kotlin.time.Instant

/**
 * Binds request parameters to [Instant]: an ISO-8601 date-time with an offset (`2026-01-23T21:15:00Z`,
 * `2026-01-23T22:15+01:00`), or, as the API accepted before it wrote offsets, one without offset, read as UTC
 * (`2026-01-23T21:15`). Seconds are optional.
 */
object InstantPropertyEditor : PropertyEditorSupport() {

    override fun setAsText(text: String) {
        value = runCatching { Instant.parse(text) }.getOrElse { LocalDateTime.parse(text).toInstant(TimeZone.UTC) }
    }
}
