package com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno

import com.federicopeyrani.trainapis.mockEngine
import com.federicopeyrani.trainapis.shared.infrastructure.cache.Cache
import com.federicopeyrani.trainapis.upstreamPath
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock

/**
 * The station registry over hand-written region lists: what it serves, and what it leaves to direct lookups.
 */
class ViaggiaTrenoStationRegistryTests {

    private fun station(id: String, name: String, lat: Double?, lon: Double?) =
        """{"codiceStazione":"$id","codStazione":"$id","codReg":1,"lat":$lat,"lon":$lon,"localita":{"nomeLungo":"$name","nomeBreve":"$name","id":"$id"}}"""

    private val lombardia = "[${station("S01700", "MILANO CENTRALE", 45.486, 9.204)},${station("S01026", "SESTO CALENDE", 45.72, 8.63)}]"
    private val piemonte = "[${station("S01026", "SESTO CALENDE", 45.72, 8.63)},${station("S00097", "TORINO CORSO GROSSETO", 0.0, 0.0)}]"

    private fun registry(statuses: Map<String, HttpStatusCode> = emptyMap()): Pair<ViaggiaTrenoStationRegistry, () -> List<String>> {
        val engine = mockEngine(
            statuses = statuses,
            bodies = mapOf("elencoStazioni/1" to lombardia, "elencoStazioni/3" to piemonte),
        )
        val registry = ViaggiaTrenoStationRegistry(ViaggiaTrenoClients.stationsApi(engine), Cache(Clock.System), regions = listOf(1, 3))
        return registry to { engine.requestHistory.map { it.upstreamPath() } }
    }

    @Test
    fun `serves stations of every region from a single load`() = runTest {
        val (registry, requests) = registry()

        assertEquals("MILANO CENTRALE", registry.find("S01700")?.localita?.nomeLungo)
        assertEquals("SESTO CALENDE", registry.find("S01026")?.localita?.nomeLungo, "listed in two regions")
        assertEquals(listOf("elencoStazioni/1", "elencoStazioni/3"), requests().sorted())
    }

    @Test
    fun `leaves stations without coordinates, or not listed, to direct lookups`() = runTest {
        val (registry, _) = registry()

        assertEquals("S01700", registry.find("S01700")?.codStazione, "the lists did load")
        assertNull(registry.find("S00097"), "listed without coordinates")
        assertNull(registry.find("S99999"), "not listed")
    }

    @Test
    fun `remembers that the lists are unavailable instead of reloading them on every lookup`() = runTest {
        val (registry, requests) = registry(statuses = mapOf("elencoStazioni/3" to HttpStatusCode.Forbidden))

        assertNull(registry.find("S01700"), "a partial load is not used")
        val afterFailure = requests().size
        assertNull(registry.find("S01026"))
        assertEquals(afterFailure, requests().size)
    }
}
