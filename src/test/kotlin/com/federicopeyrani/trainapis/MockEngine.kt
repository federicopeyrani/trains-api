package com.federicopeyrani.trainapis

import com.federicopeyrani.trainapis.utils.log
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
import io.ktor.http.headers
import io.ktor.http.headersOf
import kotlin.io.path.Path
import kotlin.io.path.createFile

private val log = MockEngine.log()

private val charactersMapping = mapOf(
    "/" to "_",
    "%20" to "-",
    "+" to "-",
    ":" to "-",
)

val client = HttpClient()

fun mockEngine(basePath: String): HttpClientEngine = MockEngine { request ->
    val clazz = this::class.java

    val resourceName = getResourceName(request)
    val resourcePath = "$basePath/${resourceName}"

    val inputStream = clazz.getResourceAsStream(resourcePath) ?: run {
        log.warn("Resource not found: $resourcePath")

        val response = downloadResource(request)
        val body = response.bodyAsText()

        clazz.getResource(basePath)
            ?.path
            ?.let { Path("$it/$resourceName") }
            ?.createFile()
            ?.toFile()
            ?.writeText(body)

        return@MockEngine respond(
            status = response.status,
            content = body,
            headers = response.headers
        )
    }

    log.info("Mocking response for ${request.method} ${request.url.encodedPath} with resource $resourcePath")

    val responseText = inputStream
        .bufferedReader()
        .use {
            it.readText()
                .trim()
        }

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

    return "${request.method}$path.json"
}

private suspend fun downloadResource(request: HttpRequestData) = client.request(request.url) {
    method = request.method
    headers {
        appendAll(request.headers)
    }
}
