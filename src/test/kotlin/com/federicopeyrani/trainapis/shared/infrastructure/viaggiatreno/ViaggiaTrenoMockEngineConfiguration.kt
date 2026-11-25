package com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno

import com.federicopeyrani.trainapis.mockEngine
import io.ktor.client.engine.HttpClientEngine
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@TestConfiguration
class ViaggiaTrenoMockEngineConfiguration {

    @Bean
    fun engine(): HttpClientEngine = mockEngine("/providers/viaggiatreno")
}
