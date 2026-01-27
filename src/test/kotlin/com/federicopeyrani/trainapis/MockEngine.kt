package com.federicopeyrani.trainapis

import com.federicopeyrani.trainapis.utils.log
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondBadRequest
import io.ktor.http.headersOf

private val log = MockEngine.log()

private val charactersMapping = mapOf(
    "/" to "_",
    "%20" to "-",
    "+" to "-",
    ":" to "-",
)

fun mockEngine(basePath: String): HttpClientEngine = MockEngine { request ->
    val parsedPath = charactersMapping.entries.fold(request.url.encodedPath) { path, (char, replacement) ->
        path.replace(
            char,
            replacement
        )
    }

    val resourcePath = "$basePath/${request.method}${parsedPath}.json"

    val inputStream = this::class.java.getResourceAsStream(resourcePath) ?: run {
        log.warn("Resource not found: $resourcePath")
        return@MockEngine respondBadRequest()
    }

    val responseText = inputStream.bufferedReader().use { it.readText() }

    respond(
        content = responseText,
        headers = headersOf("Content-Type" to listOf("application/json"))
    )
}
