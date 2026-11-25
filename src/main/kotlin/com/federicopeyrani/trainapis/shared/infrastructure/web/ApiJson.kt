package com.federicopeyrani.trainapis.shared.infrastructure.web

import kotlinx.serialization.json.Json

/**
 * The JSON format of the public API: response bodies, and the data of server-sent events that are encoded by hand.
 * Optional fields left to their default (`null`) are omitted.
 */
val ApiJson: Json = Json.Default
