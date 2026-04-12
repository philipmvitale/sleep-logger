package com.noom.interview.fullstack.sleep.model

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

data class SleepLog(
    val id: Long? = null,
    val userId: Long,
    val mood: Mood,
    val bedTime: OffsetDateTime,
    val bedTimeZone: ZoneId,
    val wakeTime: OffsetDateTime,
    val wakeTimeZone: ZoneId,
    val createdAt: Instant? = null
)
