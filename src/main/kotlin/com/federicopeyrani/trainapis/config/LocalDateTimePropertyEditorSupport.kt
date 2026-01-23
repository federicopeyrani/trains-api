package com.federicopeyrani.trainapis.config

import org.springframework.stereotype.Component
import java.beans.PropertyEditorSupport

/**
 * Property editor for converting String to kotlinx.datetime.LocalDateTime.
 * Used for form parameters and path variables.
 */
@Component
class LocalDateTimePropertyEditorSupport : PropertyEditorSupport() {

    override fun setAsText(text: String) {
        val localDateTime = LocalDateTimeFormats.ISO_FORMAT.parse(text)
        setValue(localDateTime)
    }
}

