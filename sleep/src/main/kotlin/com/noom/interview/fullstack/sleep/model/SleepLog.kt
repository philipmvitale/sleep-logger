package com.noom.interview.fullstack.sleep.model

import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * A persisted sleep log entry. Timestamps are stored with their original time-zone context,
 * so statistics can be computed in the user's local time.
 *
 * @property id database-generated primary key, `null` before persistence
 * @property bedTimeZone the user's time zone at the time they went to bed
 * @property wakeTimeZone the user's time zone at the time they woke up
 * @property durationMinutes computed sleep duration derived from [bedTime] and [wakeTime]
 */
data class SleepLog(
    val id: Long? = null,
    val userId: Long,
    val mood: Mood,
    val bedTime: OffsetDateTime,
    val bedTimeZone: ZoneId,
    val wakeTime: OffsetDateTime,
    val wakeTimeZone: ZoneId
) {
    val durationMinutes: Int
        get() = Duration.between(bedTime, wakeTime).toMinutes().toInt()
}
