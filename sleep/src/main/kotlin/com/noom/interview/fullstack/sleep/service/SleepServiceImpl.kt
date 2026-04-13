package com.noom.interview.fullstack.sleep.service

import com.noom.interview.fullstack.sleep.exception.ResourceConflictException
import com.noom.interview.fullstack.sleep.exception.ResourceNotFoundException
import com.noom.interview.fullstack.sleep.exception.SleepLogInvalidException
import com.noom.interview.fullstack.sleep.model.NewSleepLog
import com.noom.interview.fullstack.sleep.model.SleepLog
import com.noom.interview.fullstack.sleep.model.SleepStats
import com.noom.interview.fullstack.sleep.model.User
import com.noom.interview.fullstack.sleep.repository.SleepLogRepository
import com.noom.interview.fullstack.sleep.repository.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

private val logger = KotlinLogging.logger {}

/**
 * Default implementation of [SleepService].
 *
 * "Today" is always resolved relative to the user's configured time zone via [Clock],
 * ensuring that a user in Tokyo and a user in New York each see their own calendar day.
 */
@Service
class SleepServiceImpl(
    private val sleepLogRepository: SleepLogRepository,
    private val userRepository: UserRepository,
    private val clock: Clock
) : SleepService {

    companion object {
        val MINIMUM_SLEEP_DURATION: Duration = Duration.ofMinutes(30)
        val MAXIMUM_SLEEP_DURATION: Duration = Duration.ofHours(24)
        const val STATS_WINDOW_DAYS = 30L
    }

    @Transactional
    override fun createTodaySleepLog(userId: Long, newSleepLog: NewSleepLog): SleepLog {
        val user = findUserOrThrow(userId)
        val sleepDuration = newSleepLog.duration
        if (sleepDuration.isNegative || sleepDuration.isZero) {
            logger.warn { "userId=$userId: bed time ${newSleepLog.bedTime} is not before wake time ${newSleepLog.wakeTime}" }
            throw SleepLogInvalidException("Bed time must be before wake time")
        }
        if (sleepDuration < MINIMUM_SLEEP_DURATION) {
            logger.warn { "userId=$userId: sleep duration ${sleepDuration.toMinutes()}min is below minimum of ${MINIMUM_SLEEP_DURATION.toMinutes()}min" }
            throw SleepLogInvalidException("Sleep duration must be at least ${MINIMUM_SLEEP_DURATION.toMinutes()} minutes")
        }
        if (sleepDuration >= MAXIMUM_SLEEP_DURATION) {
            logger.warn { "userId=$userId: sleep duration exceeds 24 hours (bed=${newSleepLog.bedTime}, wake=${newSleepLog.wakeTime})" }
            throw SleepLogInvalidException("Sleep duration must be less than 24 hours")
        }
        val startOfUserDay = startOfDayUtc(user.timeZone)
        if (newSleepLog.wakeTime.isBefore(startOfUserDay)) {
            logger.warn { "userId=$userId: wake time ${newSleepLog.wakeTime} is before start of user day $startOfUserDay" }
            throw SleepLogInvalidException("Wake time must be today for the user's time zone")
        }

        // IMPORTANT: This overlap check only examines the latest log. It is safe ONLY because
        // the "wake time must be today" validation above guarantees new logs are appended
        // chronologically. If that rule is ever relaxed (e.g., to allow backdating), this
        // MUST be replaced with a range-based overlap query against all existing logs —
        // otherwise overlapping entries will silently bypass this check and only be caught
        // by the DB exclusion constraint.
        val existing = sleepLogRepository.findLatestSleepLogByUserId(userId)
        if (existing != null) {
            if (!existing.wakeTime.isBefore(startOfUserDay)) {
                logger.warn { "userId=$userId: sleep log already exists for today (existingId=${existing.id})" }
                throw ResourceConflictException("A sleep log already exists for today.")
            }
            if (newSleepLog.bedTime.isBefore(existing.wakeTime)) {
                logger.warn { "userId=$userId: new sleep log overlaps with existing entry (existingId=${existing.id})" }
                throw SleepLogInvalidException("New sleep log would overlap with existing entry")
            }
        }

        val sleepLog = SleepLog(
            userId = userId,
            bedTime = newSleepLog.bedTime,
            bedTimeZone = user.timeZone,
            wakeTime = newSleepLog.wakeTime,
            wakeTimeZone = user.timeZone,
            mood = newSleepLog.mood
        )

        val saved = sleepLogRepository.saveSleepLog(sleepLog)
        logger.info { "Created sleep log id=${saved.id} for userId=$userId, duration=${saved.duration.toMinutes()}min, mood=${saved.mood}" }
        return saved
    }

    override fun getTodaySleepLog(userId: Long): SleepLog {
        val user = findUserOrThrow(userId)
        val startOfUserDay = startOfDayUtc(user.timeZone)
        val existing = sleepLogRepository.findLatestSleepLogByUserId(userId)
        // Both sides are UTC-normalized, so comparisons work regardless of offset.
        // A wake time exactly at the startOfUserDay (>= boundary) counts as "today",
        // consistent with the >= used in getSleepStats range queries.
        if (existing == null || existing.wakeTime.isBefore(startOfUserDay)) {
            throw ResourceNotFoundException("No sleep log found for today.")
        }

        return existing
    }

    override fun getSleepStats(userId: Long): SleepStats {
        val user = findUserOrThrow(userId)
        val startOfUserDay = startOfDayUtc(user.timeZone)
        val from = startOfUserDay.minusDays(STATS_WINDOW_DAYS - 1)
        val to = startOfUserDay.plusDays(1)
        val logs = sleepLogRepository.findSleepLogsByUserIdAndWakeTimeRange(userId, from, to)

        if (logs.isEmpty()) {
            logger.debug { "No sleep logs found for userId=$userId in 30-day window ($from to $to)" }
            return SleepStats(
                dateFrom = from,
                dateTo = to,
                averageDurationMinutes = null,
                averageBedTime = null,
                averageWakeTime = null,
                moodFrequencies = emptyMap()
            )
        }

        val avgDurationMinutes =
            logs.map { it.duration.toMinutes() }.average().roundToLong()

        val avgBedTime = circularAverageTime(
            logs.map { it.bedTime.atZoneSameInstant(it.bedTimeZone).toLocalTime() }
        )
        val avgWakeTime = circularAverageTime(
            logs.map { it.wakeTime.atZoneSameInstant(it.wakeTimeZone).toLocalTime() }
        )

        val moodFrequencies = logs.groupingBy { it.mood }.eachCount()

        return SleepStats(
            dateFrom = from,
            dateTo = to,
            averageDurationMinutes = avgDurationMinutes,
            averageBedTime = avgBedTime,
            averageWakeTime = avgWakeTime,
            moodFrequencies = moodFrequencies
        )
    }

    private fun findUserOrThrow(userId: Long): User =
        userRepository.findUserById(userId) ?: throw ResourceNotFoundException("User not found")

    /**
     * Returns the start of the day in the given time zone as an OffsetDateTime in UTC.
     *
     * The intermediate [toOffsetDateTime] carries the zone's local offset (e.g. -05:00 for New York),
     * then [withOffsetSameInstant] normalises to UTC so all comparisons use a single offset.
     */
    private fun startOfDayUtc(timeZone: ZoneId): OffsetDateTime =
        LocalDate.now(clock.withZone(timeZone))
            .atStartOfDay(timeZone)
            .toOffsetDateTime()
            .withOffsetSameInstant(ZoneOffset.UTC)

    /**
     * Computes the circular mean of the given times, using noon as the reference point.
     *
     * A naive arithmetic mean will break for times that wrap around midnight (e.g., 23:00 and 01:00
     * would average to 12:00 instead of the expected 00:00). Treating each time as an angle on a
     * 24-hour circle and averaging the unit vectors avoids this.
     *
     * The noon offset shifts the "wrap seam" from midnight to noon, which is appropriate because
     * bed times cluster around midnight while wake times cluster around morning — neither cross noon.
     * This assumes conventional sleep schedules; shift workers who regularly sleep through noon
     * (e.g., noon-to-8 PM) would need a different reference point.
     *
     * @see <a href="https://en.wikipedia.org/wiki/Circular_mean">Circular mean (Wikipedia)</a>
     */
    private fun circularAverageTime(times: List<LocalTime>): LocalTime {
        require(times.isNotEmpty()) { "Cannot compute circular average of empty list" }
        val secondsInDay = 24 * 60 * 60.0
        // Shift the origin to noon so the midnight-crossing seam falls at noon,
        // where neither bed times nor wake times are expected to land.
        val noonOffsetSeconds: Long = 12 * 60 * 60

        var sinSum = 0.0
        var cosSum = 0.0

        for (time in times) {
            val offsetSeconds = (time.toSecondOfDay() - noonOffsetSeconds).mod(secondsInDay.toLong())
            val angle = 2.0 * Math.PI * offsetSeconds.toDouble() / secondsInDay
            sinSum += sin(angle)
            cosSum += cos(angle)
        }

        val avgAngle = atan2(sinSum, cosSum)
        val avgOffsetSeconds = (avgAngle / (2.0 * Math.PI) * secondsInDay).roundToLong().mod(secondsInDay.toLong())
        val avgSeconds = (avgOffsetSeconds + noonOffsetSeconds).mod(secondsInDay.toLong())

        return LocalTime.ofSecondOfDay(avgSeconds)
    }
}
