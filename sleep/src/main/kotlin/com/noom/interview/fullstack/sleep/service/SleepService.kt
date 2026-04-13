package com.noom.interview.fullstack.sleep.service

import com.noom.interview.fullstack.sleep.model.NewSleepLog
import com.noom.interview.fullstack.sleep.model.SleepLog
import com.noom.interview.fullstack.sleep.model.SleepStats

/**
 * Service for managing user sleep logs and computing sleep statistics.
 */
interface SleepService {

    /**
     * Creates a sleep log for today for the given user.
     *
     * @param userId the ID of the user creating the log
     * @param newSleepLog the bedtime, wake time, and mood to record
     * @return the persisted [SleepLog] with generated ID and timestamps
     * @throws com.noom.interview.fullstack.sleep.exception.ResourceNotFoundException if the user does not exist
     * @throws com.noom.interview.fullstack.sleep.exception.ResourceConflictException if a sleep log already exists for today
     * @throws com.noom.interview.fullstack.sleep.exception.SleepLogInvalidException if the sleep log fails validation
     */
    fun createTodaySleepLog(userId: Long, newSleepLog: NewSleepLog): SleepLog

    /**
     * Retrieves the most recent sleep log for the given user, provided it falls on today's date.
     *
     * @param userId the ID of the user
     * @return today's [SleepLog]
     * @throws com.noom.interview.fullstack.sleep.exception.ResourceNotFoundException if the user does not exist or no sleep log exists for today
     */
    fun getTodaySleepLog(userId: Long): SleepLog

    /**
     * Computes aggregate sleep statistics for the given user over the last 30 days
     * (today and the 29 days before it).
     *
     * @param userId the ID of the user
     * @return a [SleepStats] containing average duration, average bed/wake times, and mood frequencies
     * @throws com.noom.interview.fullstack.sleep.exception.ResourceNotFoundException if the user does not exist
     */
    fun getSleepStats(userId: Long): SleepStats
}
