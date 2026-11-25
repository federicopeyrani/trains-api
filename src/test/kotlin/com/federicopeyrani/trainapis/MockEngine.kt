package com.federicopeyrani.trainapis

import com.federicopeyrani.trainapis.shared.util.log
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.headersOf

private val log = MockEngine.log()

private val charactersMapping = mapOf(
    "/" to "_",
    "%20" to "-",
    "+" to "-",
    ":" to "-",
)

/**
 * Fake HTTP engine serving recorded responses from the test classpath.
 *
 * A request for `GET /a/b` is answered with `$basePath/GET_a_b.json`. Missing fixtures fail the request
 * immediately: record them with `curl` against the real API and commit them under `src/test/resources$basePath`.
 */
fun mockEngine(basePath: String): HttpClientEngine = MockEngine { request ->
    val resourceName = getResourceName(request)
    val resourcePath = "$basePath/$resourceName"

    val inputStream = this::class.java.getResourceAsStream(resourcePath)
        ?: error("Missing test fixture for ${request.method.value} ${request.url}: add src/test/resources$resourcePath")

    log.info("Mocking response for ${request.method} ${request.url.encodedPath} with resource $resourcePath")

    val responseText = inputStream
        .bufferedReader()
        .use { it.readText().trim() }

    respond(
        content = responseText,
        headers = headersOf("Content-Type" to listOf("application/json"))
    )
}

private fun getResourceName(request: HttpRequestData): String {
    val path = charactersMapping
        .entries
        .fold(request.url.encodedPath) { path, (char, replacement) ->
            path.replace(char, replacement)
        }

    return "${request.method.value}$path.json"
}
