package com.noom.interview.fullstack.sleep.exception

/**
 * Thrown when a requested resource (user, sleep log) does not exist. Mapped to HTTP 404.
 */
class ResourceNotFoundException(message: String) : RuntimeException(message)
