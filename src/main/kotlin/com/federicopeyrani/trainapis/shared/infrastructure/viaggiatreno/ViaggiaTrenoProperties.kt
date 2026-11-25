package com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * @property concurrency maximum number of concurrent upstream calls when fanning out per departure/station
 */
@ConfigurationProperties("providers.viaggiatreno")
data class ViaggiaTrenoProperties(
    val concurrency: Int = 10,
)
