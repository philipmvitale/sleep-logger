package com.noom.interview.fullstack.sleep.exception

/**
 * Thrown when an operation would violate a uniqueness constraint
 * e.g., creating a second sleep log for the same day. Mapped to HTTP 409.
 */
class ResourceConflictException(message: String) : RuntimeException(message)
