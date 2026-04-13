package com.noom.interview.fullstack.sleep.exception

/**
 * Thrown when a sleep log fails business-rule validation (e.g., bedtime after wake time,
 * duration exceeds 24 hours, or overlapping entries). Mapped to HTTP 400.
 */
class SleepLogInvalidException(message: String) : RuntimeException(message)
