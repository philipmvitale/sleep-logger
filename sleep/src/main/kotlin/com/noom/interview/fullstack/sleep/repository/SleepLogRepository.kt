package com.noom.interview.fullstack.sleep.repository

import com.noom.interview.fullstack.sleep.model.SleepLog
import java.time.OffsetDateTime

interface SleepLogRepository {
    fun saveSleepLog(sleepLog: SleepLog): SleepLog
    fun findLatestSleepLogByUserId(userId: Long): SleepLog?
    fun findSleepLogsByUserIdAndWakeTimeRange(userId: Long, from: OffsetDateTime, to: OffsetDateTime): List<SleepLog>
}
