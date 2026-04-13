package com.noom.interview.fullstack.sleep.controller

import com.noom.interview.fullstack.sleep.api.SleepApi
import com.noom.interview.fullstack.sleep.api.model.CreateSleepLogRequest
import com.noom.interview.fullstack.sleep.api.model.MoodFrequencies
import com.noom.interview.fullstack.sleep.api.model.SleepLogResponse
import com.noom.interview.fullstack.sleep.api.model.SleepStatsResponse
import com.noom.interview.fullstack.sleep.model.Mood
import com.noom.interview.fullstack.sleep.model.NewSleepLog
import com.noom.interview.fullstack.sleep.model.SleepLog
import com.noom.interview.fullstack.sleep.model.SleepStats
import com.noom.interview.fullstack.sleep.service.SleepService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import com.noom.interview.fullstack.sleep.api.model.Mood as ApiMood

/**
 * REST controller implementing the OpenAPI-generated [SleepApi] interface.
 *
 * Responsible only for DTO-to-domain mapping and HTTP status selection;
 * all business logic is delegated to [SleepService].
 */
@RestController
class SleepController(
    private val sleepService: SleepService
) : SleepApi {

    override fun createSleepLog(
        xUserId: Long,
        createSleepLogRequest: CreateSleepLogRequest
    ): ResponseEntity<SleepLogResponse> {
        val newSleepLog = toNewSleepLog(createSleepLogRequest)
        val sleepLog = sleepService.createTodaySleepLog(xUserId, newSleepLog)
        return ResponseEntity.status(HttpStatus.CREATED).body(toSleepLogResponse(sleepLog))
    }

    override fun getSleepLog(xUserId: Long): ResponseEntity<SleepLogResponse> {
        val sleepLog = sleepService.getTodaySleepLog(xUserId)
        return ResponseEntity.ok(toSleepLogResponse(sleepLog))
    }

    override fun getSleepStats(xUserId: Long): ResponseEntity<SleepStatsResponse> {
        val stats = sleepService.getSleepStats(xUserId)
        return ResponseEntity.ok(toSleepStatsResponse(stats))
    }

    /** Converts the generated API request DTO to the domain input model. */
    private fun toNewSleepLog(request: CreateSleepLogRequest): NewSleepLog {
        return NewSleepLog(
            bedTime = request.bedTime,
            wakeTime = request.wakeTime,
            mood = Mood.valueOf(request.mood.value)
        )
    }

    /** Converts a domain [SleepLog] to the generated API response DTO. */
    private fun toSleepLogResponse(sleepLog: SleepLog): SleepLogResponse {
        return SleepLogResponse(
            bedTime = sleepLog.bedTime,
            bedTimeZone = sleepLog.bedTimeZone.id,
            wakeTime = sleepLog.wakeTime,
            wakeTimeZone = sleepLog.wakeTimeZone.id,
            durationMinutes = sleepLog.duration.toMinutes(),
            mood = ApiMood.valueOf(sleepLog.mood.name)
        )
    }

    /** Converts a domain [SleepStats] to the generated API response DTO, flattening the mood map. */
    private fun toSleepStatsResponse(stats: SleepStats): SleepStatsResponse {
        return SleepStatsResponse(
            dateFrom = stats.dateFrom,
            dateTo = stats.dateTo,
            averageDurationMinutes = stats.averageDurationMinutes,
            averageBedTime = stats.averageBedTime,
            averageWakeTime = stats.averageWakeTime,
            moodFrequencies = MoodFrequencies(
                badFrequency = stats.moodFrequencies[Mood.BAD] ?: 0,
                okFrequency = stats.moodFrequencies[Mood.OK] ?: 0,
                goodFrequency = stats.moodFrequencies[Mood.GOOD] ?: 0
            )
        )
    }
}
