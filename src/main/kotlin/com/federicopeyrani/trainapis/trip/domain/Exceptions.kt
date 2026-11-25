package com.federicopeyrani.trainapis.trip.domain

import com.federicopeyrani.trainapis.shared.domain.InvalidRequestException
import com.federicopeyrani.trainapis.shared.domain.NotFoundException

class InvalidRunIdException(value: String) : InvalidRequestException("Invalid run id '$value'")

class RunNotFoundException(runId: RunId) : NotFoundException("Run '$runId' not found")
