package com.federicopeyrani.trainapis.shared.domain

/**
 * Base class for failures the application can explain to its callers. Adapters translate the subtypes into their
 * own protocol (e.g. HTTP status codes).
 */
sealed class DomainException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * The caller asked for something that cannot exist (malformed identifier, invalid parameter, ...).
 */
open class InvalidRequestException(message: String) : DomainException(message)

/**
 * The requested resource does not exist.
 */
open class NotFoundException(message: String) : DomainException(message)

/**
 * The data provider cannot serve the request right now (rate limiting, outage, ...). Retrying later may succeed.
 */
open class ProviderUnavailableException(message: String, cause: Throwable? = null) : DomainException(message, cause)

class StationNotFoundException(stationId: StationId) : NotFoundException("Station '$stationId' not found")
