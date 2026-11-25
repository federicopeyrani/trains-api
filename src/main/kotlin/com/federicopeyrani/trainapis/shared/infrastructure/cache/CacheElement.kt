package com.federicopeyrani.trainapis.shared.infrastructure.cache

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A cached value together with the time it was fetched and how long it stays valid.
 */
data class CacheElement<T>(
    val data: T,
    val timestamp: Instant,
    val ttl: Duration,
)
