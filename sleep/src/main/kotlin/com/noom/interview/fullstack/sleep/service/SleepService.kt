package com.noom.interview.fullstack.sleep.service

import com.noom.interview.fullstack.sleep.api.model.CreateSleepLogRequest
import com.noom.interview.fullstack.sleep.api.model.SleepLogResponse
import com.noom.interview.fullstack.sleep.api.model.SleepStatsResponse

interface SleepService {
    fun createSleepLog(userId: Long, request: CreateSleepLogRequest): SleepLogResponse
    fun getSleepLog(userId: Long): SleepLogResponse
    fun getSleepStats(userId: Long): SleepStatsResponse
}
