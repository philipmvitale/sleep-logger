package com.noom.interview.fullstack.sleep.model

import java.time.OffsetDateTime

data class NewSleepLog(
    val bedTime: OffsetDateTime,
    val wakeTime: OffsetDateTime,
    val mood: Mood
)
