package com.noom.interview.fullstack.sleep.repository

import com.noom.interview.fullstack.sleep.model.SleepLog
import java.time.OffsetDateTime

/**
 * Repository for persisting and querying [SleepLog] records.
 */
interface SleepLogRepository {

    /**
     * Persists a new sleep log.
     *
     * @param sleepLog the sleep log to save
     * @return the saved [SleepLog] with its generated ID
     */
    fun saveSleepLog(sleepLog: SleepLog): SleepLog

    /**
     * Finds the most recent sleep log for the given user, ordered by wake time descending.
     *
     * @param userId the ID of the user
     * @return the latest [SleepLog], or `null` if the user has no sleep logs
     */
    fun findLatestSleepLogByUserId(userId: Long): SleepLog?

    /**
     * Finds all sleep logs for a user whose wake time falls within the given range.
     *
     * @param userId the ID of the user
     * @param from the start of the wake-time range (inclusive)
     * @param to the end of the wake-time range (exclusive)
     * @return a list of matching [SleepLog] records, possibly empty
     */
    fun findSleepLogsByUserIdAndWakeTimeRange(userId: Long, from: OffsetDateTime, to: OffsetDateTime): List<SleepLog>
}
