package com.noom.interview.fullstack.sleep.model

import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * Aggregated sleep statistics over a date range.
 *
 * @property dateFrom start of the statistics window (inclusive, UTC)
 * @property dateTo end of the statistics window (exclusive, UTC)
 * @property averageBedTime circular mean of bed times in the sleep log's local time zone
 * @property averageWakeTime circular mean of wake times in the sleep log's local time zone
 * @property moodFrequencies count of each [Mood] value within the window
 */
data class SleepStats(
    val dateFrom: OffsetDateTime,
    val dateTo: OffsetDateTime,
    val averageDurationMinutes: Int,
    val averageBedTime: LocalTime,
    val averageWakeTime: LocalTime,
    val moodFrequencies: Map<Mood, Int>
)
