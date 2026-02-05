package com.federicopeyrani.trainapis.data

import com.federicopeyrani.trainapis.providers.viaggiatreno.CacheElement
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

    val cache by lazy { cacheManager.getCache(name) ?: error("No cache found with name: $name") }

    inline operator fun <reified T> get(key: Key<T>): T? {
        return cache.get(key.asString(), CacheElement::class.java)
            ?.takeIf { Clock.System.now() - it.timestamp < it.ttl }
            ?.data as? T
    }

    fun <T> put(key: Key<T>, value: T, ttl: Duration = Duration.INFINITE) {
        val element = CacheElement.of(value, ttl)
        cache.put(key.asString(), element)
    }

    inline fun <reified T> getOrPut(key: Key<T>, ttl: Duration = Duration.INFINITE, block: () -> T): T {
        return get(key) ?: block().also { put(key, it, ttl) }
    }
}
