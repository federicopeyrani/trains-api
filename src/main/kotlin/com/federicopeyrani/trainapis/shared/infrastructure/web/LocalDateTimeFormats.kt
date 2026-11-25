package com.federicopeyrani.trainapis.shared.infrastructure.web

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format.char

/**
 * Common LocalDateTime format definitions used across the application.
 */
object LocalDateTimeFormats {

    /**
     * ISO 8601 format: yyyy-MM-ddTHH:mm:ss
     */
    val ISO_FORMAT = LocalDateTime.Format {
        year()
        char('-')
        monthNumber()
        char('-')
        day()
        char('T')
        hour()
        char(':')
        minute()
        char(':')
        second()
    }
}

