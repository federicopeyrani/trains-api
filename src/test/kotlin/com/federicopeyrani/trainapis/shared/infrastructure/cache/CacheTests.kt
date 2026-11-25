package com.federicopeyrani.trainapis.shared.infrastructure.cache

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class CacheTests {

    private class MutableClock(var now: Instant = Instant.fromEpochSeconds(0)) : Clock {

        override fun now() = now
    }

    private val clock = MutableClock()
    private val key = Cache.Key.of<Int>("key")

    @Test
    fun `concurrent callers share a single computation`() = runTest {
        val cache = Cache(clock)
        val gate = CompletableDeferred<Unit>()
        var calls = 0

        val results = List(10) {
            async {
                cache.getOrPut(key) {
                    calls++
                    gate.await()
                    42
                }.data
            }
        }
        launch { gate.complete(Unit) }

        assertEquals(List(10) { 42 }, results.awaitAll())
        assertEquals(1, calls)
    }

    @Test
    fun `entries expire after their ttl`() = runTest {
        val cache = Cache(clock)
        var calls = 0
        val get = suspend { cache.getOrPut(key, ttl = { 10.minutes }) { ++calls }.data }

        assertEquals(1, get())
        clock.now += 9.minutes
        assertEquals(1, get())
        clock.now += 1.minutes
        assertEquals(2, get())
    }

    @Test
    fun `failures are not cached`() = runTest {
        val cache = Cache(clock)

        assertFailsWith<IllegalStateException> { cache.getOrPut(key) { error("upstream down") } }
        assertEquals(1, cache.getOrPut(key) { 1 }.data)
    }

    @Test
    fun `size is bounded by evicting expired and then oldest entries`() = runTest {
        val cache = Cache(clock, maxEntries = 10)

        repeat(5) { cache.getOrPut(Cache.Key.of("expired-$it"), ttl = { 1.minutes }) { it } }
        clock.now += 2.minutes
        repeat(11) {
            clock.now += 1.minutes
            cache.getOrPut(Cache.Key.of("fresh-$it")) { it }
        }

        assertEquals(9, cache.size)
        // The oldest fresh entries were evicted, the newest one survives
        var recomputed = false
        cache.getOrPut(Cache.Key.of<Int>("fresh-10")) { recomputed = true; -1 }
        assertEquals(false, recomputed)
    }
}
