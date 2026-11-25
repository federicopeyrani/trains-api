package com.federicopeyrani.trainapis.shared.infrastructure.web

import kotlinx.datetime.LocalDateTime
import java.beans.PropertyEditorSupport

/**
 * Property editor for converting String to kotlinx.datetime.LocalDateTime.
 * Used for form parameters and path variables.
 *
 * Accepts ISO 8601 with or without seconds (`2026-01-23T21:15` or `2026-01-23T21:15:00`), so that times returned by
 * the API, which omit zero seconds, can be sent back as they are.
 */
object LocalDateTimePropertyEditorSupport : PropertyEditorSupport() {

    override fun setAsText(text: String) {
        value = LocalDateTime.parse(text)
    }
}
