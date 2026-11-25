package com.federicopeyrani.trainapis.shared.infrastructure.web

import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder
import org.springframework.web.reactive.config.WebFluxConfigurer

/**
 * Response bodies are `@Serializable` DTOs encoded with kotlinx.serialization, but WebFlux encodes server-sent event
 * data with Jackson whenever it is on the classpath. Using the same encoder keeps `text/event-stream` payloads
 * identical to `application/json` ones (ISO dates, absent optional fields).
 */
@Configuration
class WebCodecsConfig : WebFluxConfigurer {

    override fun configureHttpMessageCodecs(configurer: ServerCodecConfigurer) {
        configurer.defaultCodecs().serverSentEventEncoder(KotlinSerializationJsonEncoder(ApiJson))
    }
}
