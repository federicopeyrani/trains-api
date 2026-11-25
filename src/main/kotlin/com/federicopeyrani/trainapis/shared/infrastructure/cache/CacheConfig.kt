package com.federicopeyrani.trainapis.shared.infrastructure.cache

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.time.Clock

@Configuration
class CacheConfig {

    @Bean
    fun cache(clock: Clock) = Cache(clock)
}
