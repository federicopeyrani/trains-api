package com.federicopeyrani.trainapis.trip.domain

class InvalidRunIdException(value: String) : IllegalArgumentException("Invalid run id '$value'")
