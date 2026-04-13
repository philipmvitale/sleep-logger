package com.noom.interview.fullstack.sleep.service

import com.noom.interview.fullstack.sleep.exception.ResourceConflictException
import com.noom.interview.fullstack.sleep.exception.ResourceNotFoundException
import com.noom.interview.fullstack.sleep.exception.SleepLogInvalidException
import com.noom.interview.fullstack.sleep.model.Mood
import com.noom.interview.fullstack.sleep.model.NewSleepLog
import com.noom.interview.fullstack.sleep.model.SleepLog
import com.noom.interview.fullstack.sleep.model.User
import com.noom.interview.fullstack.sleep.repository.SleepLogRepository
import com.noom.interview.fullstack.sleep.repository.UserRepository
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class SleepServiceImplTest {

    private val repository: SleepLogRepository = mockk()
    private val userRepository: UserRepository = mockk()
    private val fixedInstant: Instant = Instant.parse("2024-01-15T12:00:00Z")
    private val fixedClock: Clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private lateinit var service: SleepServiceImpl

    private val testUser = User(id = 42L, timeZone = ZoneId.of("UTC"))
    private val today: LocalDate = LocalDate.ofInstant(fixedInstant, ZoneOffset.UTC)
    private val todayStart: OffsetDateTime = today.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime()

    @BeforeEach
    fun setUp() {
        service = SleepServiceImpl(repository, userRepository, fixedClock)
        every { userRepository.findUserById(42L) } returns testUser
    }

    @Nested
    inner class CreateSleepLog {

        @Test
        fun `creates sleep log with midnight-crossing bed time`() {
            val newSleepLog = NewSleepLog(
                bedTime = todayStart.minusDays(1).at(22, 30),
                wakeTime = todayStart.at(6, 45),
                mood = Mood.GOOD
            )
            stubSuccessfulSave()

            val result = service.createTodaySleepLog(42L, newSleepLog)

            assertThat(Duration.between(result.bedTime, result.wakeTime).toMinutes()).isEqualTo(495)
            assertThat(result.bedTime).isEqualTo(newSleepLog.bedTime)
            assertThat(result.wakeTime).isEqualTo(newSleepLog.wakeTime)
            assertThat(result.mood).isEqualTo(Mood.GOOD)
        }

        @Test
        fun `creates sleep log without midnight crossing`() {
            val newSleepLog = NewSleepLog(
                bedTime = todayStart.at(1, 0),
                wakeTime = todayStart.at(6, 45),
                mood = Mood.OK
            )
            stubSuccessfulSave()

            val result = service.createTodaySleepLog(42L, newSleepLog)

            // Both bed and wake on the same day -> 5h 45m
            assertThat(Duration.between(result.bedTime, result.wakeTime).toMinutes()).isEqualTo(345)
        }

        @Test
        fun `throws ResourceConflictException when log exists for today`() {
            val newSleepLog = NewSleepLog(
                bedTime = todayStart.minusDays(1).at(22, 30),
                wakeTime = todayStart.at(6, 45),
                mood = Mood.GOOD
            )

            every { repository.findLatestSleepLogByUserId(42L) } returns buildSleepLog()

            assertThatThrownBy { service.createTodaySleepLog(42L, newSleepLog) }
                .isInstanceOf(ResourceConflictException::class.java)
                .hasMessage("A sleep log already exists for today.")

            verify(exactly = 0) { repository.saveSleepLog(any()) }
        }

        @Test
        fun `saves sleep log with correct mood`() {
            val newSleepLog = NewSleepLog(
                bedTime = todayStart.minusDays(1).at(23, 0),
                wakeTime = todayStart.at(7, 0),
                mood = Mood.BAD
            )
            val slot = stubSuccessfulSave()

            service.createTodaySleepLog(42L, newSleepLog)

            assertThat(slot.captured.mood).isEqualTo(Mood.BAD)
        }

        @Test
        fun `throws ResourceNotFoundException when user does not exist`() {
            every { userRepository.findUserById(99L) } returns null

            val newSleepLog = NewSleepLog(
                bedTime = todayStart.minusDays(1).at(22, 30),
                wakeTime = todayStart.at(6, 45),
                mood = Mood.GOOD
            )

            assertThatThrownBy { service.createTodaySleepLog(99L, newSleepLog) }
                .isInstanceOf(ResourceNotFoundException::class.java)
                .hasMessage("User not found")
        }

        @Test
        fun `throws SleepLogInvalidException when bed time is after wake time`() {
            val newSleepLog = NewSleepLog(
                bedTime = todayStart.at(8, 0),
                wakeTime = todayStart.at(6, 0),
                mood = Mood.GOOD
            )

            assertThatThrownBy { service.createTodaySleepLog(42L, newSleepLog) }
                .isInstanceOf(SleepLogInvalidException::class.java)
                .hasMessage("Bed time must be before wake time")

            verify(exactly = 0) { repository.saveSleepLog(any()) }
        }

        @Test
        fun `throws SleepLogInvalidException when wake time is before start of user day`() {
            val newSleepLog = NewSleepLog(
                bedTime = todayStart.minusDays(1).at(20, 0),
                wakeTime = todayStart.minusDays(1).at(23, 0),
                mood = Mood.OK
            )

            assertThatThrownBy { service.createTodaySleepLog(42L, newSleepLog) }
                .isInstanceOf(SleepLogInvalidException::class.java)
                .hasMessage("Wake time must be today for the user's time zone")

            verify(exactly = 0) { repository.saveSleepLog(any()) }
        }

        @Test
        fun `throws SleepLogInvalidException when bed time equals wake time`() {
            val newSleepLog = NewSleepLog(
                bedTime = todayStart.at(6, 0),
                wakeTime = todayStart.at(6, 0),
                mood = Mood.GOOD
            )

            assertThatThrownBy { service.createTodaySleepLog(42L, newSleepLog) }
                .isInstanceOf(SleepLogInvalidException::class.java)
                .hasMessage("Bed time must be before wake time")

            verify(exactly = 0) { repository.saveSleepLog(any()) }
        }

        @Test
        fun `throws SleepLogInvalidException when sleep duration is below minimum`() {
            val newSleepLog = NewSleepLog(
                bedTime = todayStart.at(6, 0),
                wakeTime = todayStart.at(6, 20),
                mood = Mood.OK
            )

            assertThatThrownBy { service.createTodaySleepLog(42L, newSleepLog) }
                .isInstanceOf(SleepLogInvalidException::class.java)
                .hasMessage("Sleep duration must be at least 30 minutes")

            verify(exactly = 0) { repository.saveSleepLog(any()) }
        }

        @Test
        fun `allows sleep log at exactly minimum duration`() {
            val newSleepLog = NewSleepLog(
                bedTime = todayStart.at(6, 0),
                wakeTime = todayStart.at(6, 30),
                mood = Mood.OK
            )
            stubSuccessfulSave()

            val result = service.createTodaySleepLog(42L, newSleepLog)

            assertThat(Duration.between(result.bedTime, result.wakeTime).toMinutes()).isEqualTo(30)
        }

        @Test
        fun `throws SleepLogInvalidException when sleep duration exceeds 24 hours`() {
            val newSleepLog = NewSleepLog(
                bedTime = todayStart.minusDays(2).at(5, 0),
                wakeTime = todayStart.at(6, 0),
                mood = Mood.GOOD
            )

            assertThatThrownBy { service.createTodaySleepLog(42L, newSleepLog) }
                .isInstanceOf(SleepLogInvalidException::class.java)
                .hasMessage("Sleep duration must be less than 24 hours")

            verify(exactly = 0) { repository.saveSleepLog(any()) }
        }

        @Test
        fun `throws SleepLogInvalidException when sleep duration is exactly 24 hours`() {
            val newSleepLog = NewSleepLog(
                bedTime = todayStart.minusDays(1).at(6, 0),
                wakeTime = todayStart.at(6, 0),
                mood = Mood.GOOD
            )

            assertThatThrownBy { service.createTodaySleepLog(42L, newSleepLog) }
                .isInstanceOf(SleepLogInvalidException::class.java)
                .hasMessage("Sleep duration must be less than 24 hours")

            verify(exactly = 0) { repository.saveSleepLog(any()) }
        }

        @Test
        fun `throws SleepLogInvalidException when new log overlaps with existing entry`() {
            val existingLog = buildSleepLog(
                sleepDate = todayStart.minusDays(1),
                bedTime = todayStart.minusDays(2).at(22, 0),
                wakeTime = todayStart.minusDays(1).at(7, 0)
            )
            every { repository.findLatestSleepLogByUserId(42L) } returns existingLog

            val newSleepLog = NewSleepLog(
                bedTime = todayStart.minusDays(1).at(6, 0),
                wakeTime = todayStart.at(5, 0),
                mood = Mood.GOOD
            )

            assertThatThrownBy { service.createTodaySleepLog(42L, newSleepLog) }
                .isInstanceOf(SleepLogInvalidException::class.java)
                .hasMessage("New sleep log would overlap with existing entry")

            verify(exactly = 0) { repository.saveSleepLog(any()) }
        }

        @Test
        fun `throws ResourceConflictException when existing log wake time is exactly start of day`() {
            // wakeTime == startOfUserDay should count as "today" (>= boundary)
            val existingLog = buildSleepLog(
                sleepDate = todayStart,
                bedTime = todayStart.minusDays(1).at(22, 0),
                wakeTime = todayStart
            )
            every { repository.findLatestSleepLogByUserId(42L) } returns existingLog

            val newSleepLog = NewSleepLog(
                bedTime = todayStart.at(1, 0),
                wakeTime = todayStart.at(7, 0),
                mood = Mood.OK
            )

            assertThatThrownBy { service.createTodaySleepLog(42L, newSleepLog) }
                .isInstanceOf(ResourceConflictException::class.java)
                .hasMessage("A sleep log already exists for today.")

            verify(exactly = 0) { repository.saveSleepLog(any()) }
        }

        @Test
        fun `allows creation when existing log is from a prior day`() {
            val priorDayLog = buildSleepLog(
                sleepDate = todayStart.minusDays(1),
                bedTime = todayStart.minusDays(2).at(22, 0),
                wakeTime = todayStart.minusDays(1).at(6, 0)
            )
            every { repository.findLatestSleepLogByUserId(42L) } returns priorDayLog

            val newSleepLog = NewSleepLog(
                bedTime = todayStart.minusDays(1).at(22, 30),
                wakeTime = todayStart.at(6, 45),
                mood = Mood.GOOD
            )
            val slot = slot<SleepLog>()
            every { repository.saveSleepLog(capture(slot)) } answers { slot.captured.copy(id = 2L) }

            val result = service.createTodaySleepLog(42L, newSleepLog)

            assertThat(result.id).isEqualTo(2L)
        }

        @Test
        fun `accepts wake time that is today in user time zone but yesterday in UTC`() {
            // Clock is at 2024-01-15T12:00:00Z. User is in America/New_York (UTC-5).
            // NY "today" starts at 2024-01-15T05:00:00Z. A wake time of 06:00 NY =
            // 2024-01-15T11:00:00Z is today in NY, even though a naive UTC-only check
            // with a different day boundary could reject it.
            val nyZone = ZoneId.of("America/New_York")
            val nyUser = User(id = 50L, timeZone = nyZone)
            every { userRepository.findUserById(50L) } returns nyUser
            every { repository.findLatestSleepLogByUserId(50L) } returns null

            // Bed at 23:00 NY (2024-01-15T04:00Z), wake at 06:00 NY (2024-01-15T11:00Z)
            val nyTodayStart = LocalDate.of(2024, 1, 15)
                .atStartOfDay(nyZone).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC)
            val bedTime = nyTodayStart.minusHours(1) // 04:00Z = 23:00 NY previous day
            val wakeTime = nyTodayStart.plusHours(6) // 11:00Z = 06:00 NY

            val newSleepLog = NewSleepLog(bedTime = bedTime, wakeTime = wakeTime, mood = Mood.OK)

            val slot = slot<SleepLog>()
            every { repository.saveSleepLog(capture(slot)) } answers { slot.captured.copy(id = 10L) }

            val result = service.createTodaySleepLog(50L, newSleepLog)

            assertThat(result.id).isEqualTo(10L)
            assertThat(result.bedTimeZone).isEqualTo(nyZone)
        }
    }

    @Nested
    inner class GetLastNightSleep {

        @Test
        fun `returns sleep log when found`() {
            val log = buildSleepLog()

            every { repository.findLatestSleepLogByUserId(42L) } returns log

            val result = service.getTodaySleepLog(42L)

            assertThat(Duration.between(result.bedTime, result.wakeTime).toMinutes()).isEqualTo(495)
            assertThat(result.mood).isEqualTo(Mood.GOOD)
        }

        @Test
        fun `throws ResourceNotFoundException when no log exists`() {
            every { repository.findLatestSleepLogByUserId(42L) } returns null

            assertThatThrownBy { service.getTodaySleepLog(42L) }
                .isInstanceOf(ResourceNotFoundException::class.java)
                .hasMessage("No sleep log found for today.")
        }

        @Test
        fun `throws ResourceNotFoundException when user does not exist`() {
            every { userRepository.findUserById(99L) } returns null

            assertThatThrownBy { service.getTodaySleepLog(99L) }
                .isInstanceOf(ResourceNotFoundException::class.java)
                .hasMessage("User not found")
        }

        @Test
        fun `returns sleep log when wake time is exactly start of day`() {
            // wakeTime == startOfUserDay should count as "today" (>= boundary)
            val log = buildSleepLog(
                sleepDate = todayStart,
                bedTime = todayStart.minusDays(1).at(22, 0),
                wakeTime = todayStart
            )

            every { repository.findLatestSleepLogByUserId(42L) } returns log

            val result = service.getTodaySleepLog(42L)

            assertThat(result.wakeTime).isEqualTo(todayStart)
        }

        @Test
        fun `throws ResourceNotFoundException when log exists but is from a prior day`() {
            val yesterdayLog = buildSleepLog(
                sleepDate = todayStart.minusDays(1),
                bedTime = todayStart.minusDays(2).at(22, 0),
                wakeTime = todayStart.minusDays(1).at(6, 0)
            )
            every { repository.findLatestSleepLogByUserId(42L) } returns yesterdayLog

            assertThatThrownBy { service.getTodaySleepLog(42L) }
                .isInstanceOf(ResourceNotFoundException::class.java)
                .hasMessage("No sleep log found for today.")
        }
    }

    @Nested
    inner class GetSleepAverages {

        @Test
        fun `returns null averages when no logs exist`() {
            every { repository.findSleepLogsByUserIdAndWakeTimeRange(42L, any(), any()) } returns emptyList()

            val result = service.getSleepStats(42L)

            assertThat(result.averageDurationMinutes).isNull()
            assertThat(result.averageBedTime).isNull()
            assertThat(result.averageWakeTime).isNull()
            assertThat(result.moodFrequencies).isEmpty()
        }

        @Test
        fun `queries repository with 30-day range`() {
            val thirtyDaysAgo = today.minusDays(29)
            val tomorrow = today.plusDays(1)

            every { repository.findSleepLogsByUserIdAndWakeTimeRange(42L, any(), any()) } returns emptyList()

            service.getSleepStats(42L)

            verify {
                repository.findSleepLogsByUserIdAndWakeTimeRange(
                    42L,
                    match { it.toLocalDate() == thirtyDaysAgo },
                    match { it.toLocalDate() == tomorrow }
                )
            }
        }

        @Test
        fun `computes correct average duration`() {
            val logs = listOf(
                buildSleepLog(
                    id = 1,
                    sleepDate = todayStart,
                    bedTime = todayStart.minusDays(1).at(22, 0),
                    wakeTime = todayStart.at(6, 0)
                ), // 8h = 480m
                buildSleepLog(
                    id = 2,
                    sleepDate = todayStart.minusDays(1),
                    bedTime = todayStart.minusDays(2).at(23, 0),
                    wakeTime = todayStart.minusDays(1).at(6, 0)
                ) // 7h = 420m
            )

            every { repository.findSleepLogsByUserIdAndWakeTimeRange(42L, any(), any()) } returns logs

            val result = service.getSleepStats(42L)

            assertThat(result.averageDurationMinutes).isEqualTo(450) // avg(480, 420) = 450 = 7h 30m
        }

        @Test
        fun `computes mood frequency counts`() {
            val logs = listOf(
                buildSleepLog(id = 1, sleepDate = todayStart, mood = Mood.GOOD),
                buildSleepLog(id = 2, sleepDate = todayStart.minusDays(1), mood = Mood.GOOD),
                buildSleepLog(id = 3, sleepDate = todayStart.minusDays(2), mood = Mood.BAD),
                buildSleepLog(id = 4, sleepDate = todayStart.minusDays(3), mood = Mood.OK)
            )

            every { repository.findSleepLogsByUserIdAndWakeTimeRange(42L, any(), any()) } returns logs

            val result = service.getSleepStats(42L)

            assertThat(result.moodFrequencies[Mood.GOOD]).isEqualTo(2)
            assertThat(result.moodFrequencies[Mood.BAD]).isEqualTo(1)
            assertThat(result.moodFrequencies[Mood.OK]).isEqualTo(1)
        }

        @Test
        fun `omits moods with no occurrences from frequencies`() {
            val logs = listOf(
                buildSleepLog(id = 1, sleepDate = todayStart, mood = Mood.GOOD)
            )

            every { repository.findSleepLogsByUserIdAndWakeTimeRange(42L, any(), any()) } returns logs

            val result = service.getSleepStats(42L)

            assertThat(result.moodFrequencies).containsOnlyKeys(Mood.GOOD)
            assertThat(result.moodFrequencies[Mood.GOOD]).isEqualTo(1)
        }

        @Test
        fun `computes circular average bed time across midnight`() {
            // 23:00 and 01:00 should average to 00:00, not 12:00
            val logs = listOf(
                buildSleepLog(
                    id = 1,
                    sleepDate = todayStart,
                    bedTime = todayStart.minusDays(1).at(23, 0),
                    wakeTime = todayStart.at(7, 0)
                ),
                buildSleepLog(
                    id = 2,
                    sleepDate = todayStart.minusDays(1),
                    bedTime = todayStart.minusDays(1).at(1, 0),
                    wakeTime = todayStart.minusDays(1).at(7, 0)
                )
            )

            every { repository.findSleepLogsByUserIdAndWakeTimeRange(42L, any(), any()) } returns logs

            val result = service.getSleepStats(42L)

            assertThat(result.averageBedTime).isEqualTo(LocalTime.MIDNIGHT)
        }

        @Test
        fun `computes average wake time for consistent wake times`() {
            val logs = listOf(
                buildSleepLog(
                    id = 1,
                    sleepDate = todayStart,
                    wakeTime = todayStart.at(7, 0)
                ),
                buildSleepLog(
                    id = 2,
                    sleepDate = todayStart.minusDays(1),
                    wakeTime = todayStart.minusDays(1).at(7, 0)
                )
            )

            every { repository.findSleepLogsByUserIdAndWakeTimeRange(42L, any(), any()) } returns logs

            val result = service.getSleepStats(42L)

            assertThat(result.averageWakeTime).isEqualTo(LocalTime.of(7, 0))
        }

        @Test
        fun `sets correct date range in response`() {
            every { repository.findSleepLogsByUserIdAndWakeTimeRange(42L, any(), any()) } returns emptyList()

            val result = service.getSleepStats(42L)

            assertThat(result.dateFrom).isEqualTo(todayStart.minusDays(29))
            assertThat(result.dateTo).isEqualTo(todayStart.plusDays(1))
        }

        @Test
        fun `throws ResourceNotFoundException when user does not exist`() {
            every { userRepository.findUserById(99L) } returns null

            assertThatThrownBy { service.getSleepStats(99L) }
                .isInstanceOf(ResourceNotFoundException::class.java)
                .hasMessage("User not found")
        }

        @Test
        fun `computes correct averages with single log`() {
            val logs = listOf(
                buildSleepLog(
                    id = 1,
                    sleepDate = todayStart,
                    bedTime = todayStart.minusDays(1).at(23, 0),
                    wakeTime = todayStart.at(7, 0)
                )
            )

            every { repository.findSleepLogsByUserIdAndWakeTimeRange(42L, any(), any()) } returns logs

            val result = service.getSleepStats(42L)

            assertThat(result.averageDurationMinutes).isEqualTo(480)
            assertThat(result.averageBedTime).isEqualTo(LocalTime.of(23, 0))
            assertThat(result.averageWakeTime).isEqualTo(LocalTime.of(7, 0))
            assertThat(result.moodFrequencies).containsEntry(Mood.GOOD, 1)
        }

        @Test
        fun `uses user time zone for 30-day window boundaries`() {
            // Clock is at 2024-01-15T12:00:00Z. In America/New_York that's 2024-01-15T07:00-05:00,
            // so "today" is still Jan 15. The 30-day window should start at the NY start of Jan 15 - 29 days.
            val nyZone = ZoneId.of("America/New_York")
            val nyUser = User(id = 50L, timeZone = nyZone)
            every { userRepository.findUserById(50L) } returns nyUser
            every { repository.findSleepLogsByUserIdAndWakeTimeRange(50L, any(), any()) } returns emptyList()

            service.getSleepStats(50L)

            val expectedFrom = LocalDate.of(2024, 1, 15).minusDays(29)
                .atStartOfDay(nyZone).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC)
            val expectedTo = LocalDate.of(2024, 1, 15).plusDays(1)
                .atStartOfDay(nyZone).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC)

            verify {
                repository.findSleepLogsByUserIdAndWakeTimeRange(
                    50L,
                    match { it.isEqual(expectedFrom) },
                    match { it.isEqual(expectedTo) }
                )
            }
        }

        @Test
        fun `computes average wake time across different times`() {
            val logs = listOf(
                buildSleepLog(
                    id = 1,
                    sleepDate = todayStart,
                    wakeTime = todayStart.at(6, 0)
                ),
                buildSleepLog(
                    id = 2,
                    sleepDate = todayStart.minusDays(1),
                    wakeTime = todayStart.minusDays(1).at(8, 0)
                )
            )

            every { repository.findSleepLogsByUserIdAndWakeTimeRange(42L, any(), any()) } returns logs

            val result = service.getSleepStats(42L)

            assertThat(result.averageWakeTime).isEqualTo(LocalTime.of(7, 0))
        }
    }

    private fun OffsetDateTime.at(hour: Int, minute: Int): OffsetDateTime =
        withHour(hour).withMinute(minute).withSecond(0).withNano(0)

    private fun buildSleepLog(
        id: Long = 1L,
        userId: Long = 42L,
        sleepDate: OffsetDateTime = todayStart,
        bedTime: OffsetDateTime = sleepDate.minusDays(1).at(22, 30),
        wakeTime: OffsetDateTime = sleepDate.at(6, 45),
        mood: Mood = Mood.GOOD
    ) = SleepLog(
        id = id,
        userId = userId,
        bedTime = bedTime,
        bedTimeZone = ZoneId.of("UTC"),
        wakeTime = wakeTime,
        wakeTimeZone = ZoneId.of("UTC"),
        mood = mood
    )

    private fun stubSuccessfulSave(): CapturingSlot<SleepLog> {
        val slot = slot<SleepLog>()
        every { repository.findLatestSleepLogByUserId(42L) } returns null
        every { repository.saveSleepLog(capture(slot)) } answers { slot.captured.copy(id = 1L) }
        return slot
    }
}
