package com.noom.interview.fullstack.sleep

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import java.time.Clock

@SpringBootApplication
class SleepApplication {
    companion object {
        /** Spring profile that disables Flyway and real database beans for unit tests. */
        const val UNIT_TEST_PROFILE = "unittest"
    }

    /** Provides a [Clock] bean so time-dependent logic can be deterministically tested. */
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}

fun main(args: Array<String>) {
    runApplication<SleepApplication>(*args)
}
