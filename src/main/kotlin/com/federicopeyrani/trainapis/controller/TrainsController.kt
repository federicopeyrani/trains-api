package com.federicopeyrani.trainapis.controller

import com.federicopeyrani.trainapis.api.DefaultApi
import com.federicopeyrani.trainapis.model.Solution
import kotlinx.coroutines.flow.Flow
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class TrainsController : DefaultApi {

    override fun getSolutions(from: String, to: String): ResponseEntity<Flow<Solution>> {
        TODO("Not yet implemented")
    }
}

