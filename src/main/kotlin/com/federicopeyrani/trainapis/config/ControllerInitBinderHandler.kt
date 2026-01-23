package com.federicopeyrani.trainapis.config

import kotlinx.datetime.LocalDateTime
import org.springframework.web.bind.WebDataBinder
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.InitBinder

/**
 * Controller advice for registering custom property editors.
 * Handles form parameters and path variables.
 */
@ControllerAdvice
class ControllerInitBinderHandler(
    private val localDateTimePropertyEditorSupport: LocalDateTimePropertyEditorSupport,
) {

    @InitBinder
    fun initBinder(binder: WebDataBinder) = binder.run {
        registerCustomEditor(LocalDateTime::class.java, localDateTimePropertyEditorSupport)
    }
}

