package com.federicopeyrani.trainapis.trip.application.port.inbound

import com.federicopeyrani.trainapis.trip.domain.Run

fun interface GetRunUseCase {

    suspend fun getRun(runId: String): Run
}
