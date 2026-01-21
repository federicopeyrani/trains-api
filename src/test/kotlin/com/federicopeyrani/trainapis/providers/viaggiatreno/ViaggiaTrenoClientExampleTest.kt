package com.federicopeyrani.trainapis.providers.viaggiatreno

import com.federicopeyrani.trainapis.providers.viaggiatreno.client.api.StationsApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Disabled

/**
 * Example test demonstrating how to use the ViaggiaTreno API client.
 *
 * Note: These tests are disabled by default as they make real HTTP calls to the ViaggiaTreno API.
 * Remove @Disabled annotation to run them.
 */
class ViaggiaTrenoClientExampleTest {

    @Test
    fun `example - search for Milano stations`() {
        // Create the API client
        val api = StationsApi()

        // Search for stations starting with "MILANO"
        val stations = api.cercaStazione("-")

        // Print the results
        println("Found ${stations.size} stations:")
        stations.forEach { station ->
            println("  - ${station.nomeLungo} (${station.id})")
            println("    Short name: ${station.nomeBreve}")
            println("    Label: ${station.label ?: "null"}")
        }

        // Assertions (if enabled)
        assert(stations.isNotEmpty()) { "Expected to find at least one station" }
    }

    @Test
    @Disabled("This test makes a real HTTP call to the ViaggiaTreno API")
    fun `example - search for specific station`() {
        val api = StationsApi()

        // Search for "MILANO CENTRALE"
        val stations = api.cercaStazione("MILANO CENTRALE")

        stations.forEach { station ->
            println("Station: ${station.nomeLungo}")
            println("  ID: ${station.id}")
            println("  Short name: ${station.nomeBreve}")
            println("  Label: ${station.label}")
        }
    }

    @Test
    @Disabled("This test makes a real HTTP call to the ViaggiaTreno API")
    fun `example - using the Spring service wrapper`() {
        val client = ViaggiaTrenoClient()

        val stations = client.searchStations("ROMA")

        println("Found ${stations.size} stations in Rome:")
        stations.forEach { station ->
            println("  - ${station.nomeBreve}")
        }
    }
}

