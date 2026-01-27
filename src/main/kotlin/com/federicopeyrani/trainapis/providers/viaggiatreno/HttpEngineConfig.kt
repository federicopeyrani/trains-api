package com.federicopeyrani.trainapis.providers.viaggiatreno

import io.ktor.client.engine.HttpClientEngine
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class HttpEngineConfig {

    @Bean
    fun engine(): HttpClientEngine? = null
}

