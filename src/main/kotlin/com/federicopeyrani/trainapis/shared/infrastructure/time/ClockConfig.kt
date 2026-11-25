package com.federicopeyrani.trainapis.shared.infrastructure.time

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.time.Clock

/**
 * Single source of "now" for the application, so it can be replaced with a fixed clock in tests.
 */
@Configuration
class ClockConfig {

    @Bean
    fun clock(): Clock = Clock.System
}
