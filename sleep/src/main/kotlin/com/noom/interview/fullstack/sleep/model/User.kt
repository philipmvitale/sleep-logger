package com.noom.interview.fullstack.sleep.model

import java.time.ZoneId

/**
 * Represents a registered user. The [timeZone] determines the user's local "today"
 * for sleep log creation and statistics windows.
 */
data class User(
    val id: Long,
    val timeZone: ZoneId
)
