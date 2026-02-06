package com.federicopeyrani.trainapis.data

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

data class CacheElement<T>(
    val data: T,
    val timestamp: Instant,
    val ttl: Duration,
) {

    companion object {

        fun <T> of(data: T, ttl: Duration = Duration.INFINITE) = CacheElement(
            timestamp = Clock.System.now(),
            data = data,
            ttl = ttl,
        )
    }
}
