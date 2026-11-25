package com.federicopeyrani.trainapis

import com.federicopeyrani.trainapis.shared.util.log
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.decodeURLPart
import io.ktor.http.headersOf

private val log = MockEngine.log()

private val charactersMapping = mapOf(
    "/" to "_",
    "%20" to "-",
    "+" to "-",
    ":" to "-",
)

/**
 * Classpath folder holding the recorded ViaggiaTreno responses.
 */
const val VIAGGIATRENO_FIXTURES = "/providers/viaggiatreno"

/**
 * Fake HTTP engine serving recorded responses from the test classpath.
 *
 * A request for `GET /a/b` is answered with `$basePath/GET_a_b.json`. Missing fixtures fail the request
 * immediately: record them with `curl` against the real API and commit them under `src/test/resources$basePath`.
 *
 * [statuses] simulates upstream behaviour that cannot be recorded as a file (`204 No Content`, rate limiting,
 * outages): it maps the decoded path after `/viaggiatreno/` (e.g. `andamentoTreno/S01700/2185/1769122800000`) to the
 * status answered, with an empty body, instead of the fixture.
 *
 * Every request is kept in [MockEngine.requestHistory], so tests can assert on upstream traffic.
 */
fun mockEngine(
    basePath: String = VIAGGIATRENO_FIXTURES,
    statuses: Map<String, HttpStatusCode> = emptyMap(),
) = MockEngine { request ->
    statuses[request.upstreamPath()]?.let { status ->
        log.info("Mocking response for ${request.method.value} ${request.url.encodedPath} with status $status")
        return@MockEngine respond(content = "", status = status)
    }

    val resourcePath = "$basePath/${getResourceName(request)}"

    val inputStream = this::class.java.getResourceAsStream(resourcePath)
        ?: error("Missing test fixture for ${request.method.value} ${request.url}: add src/test/resources$resourcePath")

    log.info("Mocking response for ${request.method.value} ${request.url.encodedPath} with resource $resourcePath")

    val responseText = inputStream
        .bufferedReader()
        .use { it.readText().trim() }

    respond(
        content = responseText,
        headers = headersOf("Content-Type" to listOf("application/json"))
    )
}

/**
 * Decoded request path relative to the ViaggiaTreno base URL, the key used by `mockEngine(statuses = ...)`.
 */
fun HttpRequestData.upstreamPath() = url.encodedPath.decodeURLPart().substringAfter("/viaggiatreno/")

private fun getResourceName(request: HttpRequestData): String {
    val path = charactersMapping
        .entries
        .fold(request.url.encodedPath) { path, (char, replacement) ->
            path.replace(char, replacement)
        }

    return "${request.method.value}$path.json"
}
