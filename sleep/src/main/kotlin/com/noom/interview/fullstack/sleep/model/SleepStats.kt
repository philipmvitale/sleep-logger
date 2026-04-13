package com.noom.interview.fullstack.sleep.model

import java.time.LocalTime
import java.time.OffsetDateTime

data class SleepStats(
    val dateFrom: OffsetDateTime,
    val dateTo: OffsetDateTime,
    val averageDurationMinutes: Int,
    val averageBedTime: LocalTime,
    val averageWakeTime: LocalTime,
    val moodFrequencies: Map<Mood, Int>
)
