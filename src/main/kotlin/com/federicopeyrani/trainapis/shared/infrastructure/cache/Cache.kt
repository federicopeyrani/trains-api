package com.federicopeyrani.trainapis.shared.infrastructure.cache

import org.springframework.cache.CacheManager
import org.springframework.stereotype.Component
import kotlin.time.Clock
import kotlin.time.Duration

class Cache(
    private val cacheManager: CacheManager,
    private val name: String,
) {

    @Component
    class Factory(private val cacheManager: CacheManager) {

        fun create(name: String) = Cache(cacheManager, name)
    }

    fun interface Key<T> {

        @JvmInline
        private value class KeyString<T>(val value: String) : Key<T> {

            override fun asString() = value
        }

        companion object {

            fun <T> of(value: String): Key<T> = KeyString(value)
        }

        fun asString(): String
    }

    private val cache by lazy { cacheManager.getCache(name) ?: error("No cache found with name: $name") }

    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: Key<T>) = cache
        .get(key.asString(), CacheElement::class.java)
        ?.takeIf { Clock.System.now() - it.timestamp < it.ttl }
            as? CacheElement<T>

    fun <T> put(key: Key<T>, value: T, ttl: Duration = Duration.INFINITE) = CacheElement
        .of(value, ttl)
        .also { cache.put(key.asString(), it) }

    inline fun <reified T> getOrPut(
        key: Key<T>,
        ttl: (T) -> Duration = { Duration.INFINITE },
        block: () -> T,
    ) =
        get(key) ?: block().let { put(key, it, ttl(it)) }
}
