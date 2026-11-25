package com.federicopeyrani.trainapis.shared.infrastructure.web

import com.federicopeyrani.trainapis.shared.domain.DomainException
import com.federicopeyrani.trainapis.shared.domain.InvalidRequestException
import com.federicopeyrani.trainapis.shared.domain.NotFoundException
import com.federicopeyrani.trainapis.shared.domain.ProviderUnavailableException
import com.federicopeyrani.trainapis.shared.util.log
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Translates domain failures into RFC 9457 problem responses.
 *
 * Only failures raised before a streamed response starts can change the status code; adapters therefore skip
 * individual broken items instead of failing a whole stream.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    companion object {

        private val log = log()
    }

    @ExceptionHandler(DomainException::class)
    fun handle(e: DomainException): ProblemDetail {
        val status = when (e) {
            is InvalidRequestException -> HttpStatus.BAD_REQUEST
            is NotFoundException -> HttpStatus.NOT_FOUND
            is ProviderUnavailableException -> HttpStatus.SERVICE_UNAVAILABLE
        }

        if (status.is5xxServerError) log.warn("Request failed: ${e.message}", e)

        return ProblemDetail.forStatusAndDetail(status, e.message ?: status.reasonPhrase)
    }
}
