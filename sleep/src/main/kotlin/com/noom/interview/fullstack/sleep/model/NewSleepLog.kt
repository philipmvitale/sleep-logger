package com.noom.interview.fullstack.sleep.model

import java.time.OffsetDateTime

/**
 * Inbound data for creating a new sleep log, before user/time-zone enrichment.
 * The [bedTime] and [wakeTime] carry the offset provided by the client.
 */
data class NewSleepLog(
    val bedTime: OffsetDateTime,
    val wakeTime: OffsetDateTime,
    val mood: Mood
)
