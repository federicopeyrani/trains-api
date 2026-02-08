package com.federicopeyrani.trainapis.utils

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

fun LocalDateTime.convertTimeZone(from: TimeZone, to: TimeZone) = toInstant(from)
    .toLocalDateTime(to)
