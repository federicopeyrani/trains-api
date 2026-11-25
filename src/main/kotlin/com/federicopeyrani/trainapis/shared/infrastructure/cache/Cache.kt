package com.federicopeyrani.trainapis.shared.infrastructure.cache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * In-memory, coroutine-aware TTL cache.
 *
 * - **Single flight**: concurrent [getOrPut] calls for the same key share one computation instead of all
 *   hitting the upstream service.
 * - **Bounded**: once more than [maxEntries] entries are stored, expired entries are purged first, then the
 *   oldest ones, down to 90% of the limit.
 * - **Failures are not cached**: if the computation throws, the entry is dropped and waiting callers receive
 *   the same exception.
 */
class Cache(
    private val clock: Clock,
    private val maxEntries: Int = 10_000,
) {

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

    private val entries = ConcurrentHashMap<String, Deferred<CacheElement<*>>>()

    val size get() = entries.size

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> getOrPut(
        key: Key<T>,
        ttl: (T) -> Duration = { Duration.INFINITE },
        block: suspend () -> T,
    ): CacheElement<T> {
        val id = key.asString()

        while (true) {
            val existing = entries[id]

            if (existing != null) {
                val element = try {
                    existing.await()
                } catch (e: CancellationException) {
                    // The caller computing the value was cancelled: retry (and possibly compute) unless we are too
                    currentCoroutineContext().ensureActiveOrRethrow(e)
                    continue
                }

                if (element.isFresh()) return element as CacheElement<T>

                entries.remove(id, existing)
                continue
            }

            val computation = CompletableDeferred<CacheElement<*>>()
            if (entries.putIfAbsent(id, computation) != null) continue

            try {
                val value = block()
                val element = CacheElement(data = value, timestamp = clock.now(), ttl = ttl(value))
                computation.complete(element)
                evictIfNeeded()
                return element
            } catch (e: Throwable) {
                entries.remove(id, computation)
                computation.completeExceptionally(e)
                throw e
            }
        }
    }

    private fun CacheElement<*>.isFresh() = clock.now() - timestamp < ttl

    private fun evictIfNeeded() {
        if (entries.size <= maxEntries) return

        val completed = entries.entries
            .filter { it.value.isCompleted && !it.value.isCancelled }
            .map { it to completedElementOrNull(it.value) }

        completed
            .filter { (_, element) -> element == null || !element.isFresh() }
            .forEach { (entry, _) -> entries.remove(entry.key, entry.value) }

        val target = maxEntries * 9 / 10
        if (entries.size <= target) return

        completed
            .mapNotNull { (entry, element) -> element?.let { entry to it } }
            .sortedBy { (_, element) -> element.timestamp }
            .take(entries.size - target)
            .forEach { (entry, _) -> entries.remove(entry.key, entry.value) }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun completedElementOrNull(deferred: Deferred<CacheElement<*>>) =
        runCatching { deferred.getCompleted() }.getOrNull()

    private fun kotlin.coroutines.CoroutineContext.ensureActiveOrRethrow(e: CancellationException) {
        if (!isActive) throw e
    }
}
