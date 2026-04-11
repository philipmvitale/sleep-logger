/*
 * Copyright (C) 2023 Noom, Inc.
 */
package com.noom.interview.fullstack.sleep

import com.noom.interview.fullstack.sleep.api.TestApi
import com.noom.interview.fullstack.sleep.api.model.TestResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
class TestController : TestApi {
    override fun test(): ResponseEntity<TestResponse> {
        logger.info { "Hello world! from test" }
        return ResponseEntity.ok(TestResponse("Hello world!"))
    }
}
