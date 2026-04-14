package com.noom.interview.fullstack.sleep

import com.ninjasquad.springmockk.MockkBean
import com.noom.interview.fullstack.sleep.model.Mood
import com.noom.interview.fullstack.sleep.model.SleepLog
import com.noom.interview.fullstack.sleep.model.User
import com.noom.interview.fullstack.sleep.repository.SleepLogRepository
import com.noom.interview.fullstack.sleep.repository.UserRepository
import io.mockk.every
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class SleepControllerIT : AbstractIntegrationTest() {

    companion object {
        val FIXED_INSTANT: Instant = Instant.parse("2024-06-15T12:00:00Z")
        val TODAY: LocalDate = LocalDate.ofInstant(FIXED_INSTANT, ZoneOffset.UTC)
    }

    @MockkBean
    private lateinit var clock: Clock

    @Autowired
    private lateinit var jdbcTemplate: NamedParameterJdbcTemplate

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var sleepLogRepository: SleepLogRepository

    private var userId = 0L
    private var user2Id = 0L

    @BeforeEach
    fun setUp() {
        every { clock.instant() } returns FIXED_INSTANT
        every { clock.zone } returns ZoneOffset.UTC
        every { clock.withZone(any()) } answers {
            Clock.fixed(FIXED_INSTANT, firstArg<ZoneId>())
        }

        jdbcTemplate.jdbcTemplate.execute("TRUNCATE sleep_logs, users CASCADE")

        userId = userRepository.saveUser(User(id = 0, timeZone = ZoneId.of("America/New_York"))).id
        user2Id = userRepository.saveUser(User(id = 0, timeZone = ZoneId.of("America/Los_Angeles"))).id
    }

    @Nested
    inner class CreateSleepLog {

        @Test
        fun `returns 201 and persists sleep log`() {
            val bedTime = todayBedTime()
            val wakeTime = todayWakeTime()

            mockMvc.perform(
                post("/api/v1/sleep-log")
                    .header("X-User-Id", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"bedTime":"$bedTime","wakeTime":"$wakeTime","mood":"GOOD"}""")
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.durationMinutes").value(495))
                .andExpect(jsonPath("$.mood").value("GOOD"))
        }

        @Test
        fun `returns 409 when sleep log already exists for today`() {
            val bedTime = todayBedTime()
            val wakeTime = todayWakeTime()

            mockMvc.perform(
                post("/api/v1/sleep-log")
                    .header("X-User-Id", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"bedTime":"$bedTime","wakeTime":"$wakeTime","mood":"GOOD"}""")
            )
                .andExpect(status().isCreated)

            val bedTime2 = utcDateTime(TODAY.minusDays(1), 23, 0)
            val wakeTime2 = utcDateTime(TODAY, 7, 0)

            mockMvc.perform(
                post("/api/v1/sleep-log")
                    .header("X-User-Id", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"bedTime":"$bedTime2","wakeTime":"$wakeTime2","mood":"OK"}""")
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.error").value("Conflict"))
        }

        @Test
        fun `returns 400 when request body is missing required fields`() {
            mockMvc.perform(
                post("/api/v1/sleep-log")
                    .header("X-User-Id", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"bedTime":"2024-01-14T22:30:00Z"}""")
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `returns 400 when X-User-Id header is missing`() {
            val bedTime = todayBedTime()
            val wakeTime = todayWakeTime()

            mockMvc.perform(
                post("/api/v1/sleep-log")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"bedTime":"$bedTime","wakeTime":"$wakeTime","mood":"GOOD"}""")
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `returns 400 when bed time is after wake time`() {
            val bedTime = utcDateTime(TODAY, 8, 0)
            val wakeTime = utcDateTime(TODAY, 6, 0)

            mockMvc.perform(
                post("/api/v1/sleep-log")
                    .header("X-User-Id", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"bedTime":"$bedTime","wakeTime":"$wakeTime","mood":"GOOD"}""")
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Bed time must be before wake time"))
        }

        @Test
        fun `returns 400 when sleep duration is below minimum`() {
            val bedTime = utcDateTime(TODAY, 6, 0)
            val wakeTime = utcDateTime(TODAY, 6, 20)

            mockMvc.perform(
                post("/api/v1/sleep-log")
                    .header("X-User-Id", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"bedTime":"$bedTime","wakeTime":"$wakeTime","mood":"OK"}""")
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Sleep duration must be at least 30 minutes"))
        }

        @Test
        fun `returns 400 when sleep duration exceeds 24 hours`() {
            val bedTime = utcDateTime(TODAY.minusDays(2), 5, 0)
            val wakeTime = utcDateTime(TODAY, 6, 0)

            mockMvc.perform(
                post("/api/v1/sleep-log")
                    .header("X-User-Id", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"bedTime":"$bedTime","wakeTime":"$wakeTime","mood":"GOOD"}""")
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Sleep duration must be less than 24 hours"))
        }

        @Test
        fun `returns 404 when user does not exist`() {
            val bedTime = todayBedTime()
            val wakeTime = todayWakeTime()

            mockMvc.perform(
                post("/api/v1/sleep-log")
                    .header("X-User-Id", 999999L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"bedTime":"$bedTime","wakeTime":"$wakeTime","mood":"GOOD"}""")
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error").value("Not Found"))
        }
    }

    @Nested
    inner class GetLastNightSleep {

        @Test
        fun `returns 200 with sleep log after creating one`() {
            val bedTime = utcDateTime(TODAY.minusDays(1), 23, 0)
            val wakeTime = utcDateTime(TODAY, 7, 30)

            mockMvc.perform(
                post("/api/v1/sleep-log")
                    .header("X-User-Id", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"bedTime":"$bedTime","wakeTime":"$wakeTime","mood":"OK"}""")
            )
                .andExpect(status().isCreated)

            mockMvc.perform(
                get("/api/v1/sleep-log")
                    .header("X-User-Id", userId)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.mood").value("OK"))
                .andExpect(jsonPath("$.durationMinutes").value(510))
        }

        @Test
        fun `returns 404 when no sleep log exists`() {
            mockMvc.perform(
                get("/api/v1/sleep-log")
                    .header("X-User-Id", userId)
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error").value("Not Found"))
        }

        @Test
        fun `does not return another user's sleep log`() {
            createSleepLogForUser2()

            mockMvc.perform(
                get("/api/v1/sleep-log")
                    .header("X-User-Id", userId)
            )
                .andExpect(status().isNotFound)
        }

        @Test
        fun `returns 404 when user does not exist`() {
            mockMvc.perform(
                get("/api/v1/sleep-log")
                    .header("X-User-Id", 999999L)
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error").value("Not Found"))
        }
    }

    @Nested
    inner class GetSleepAverages {

        @Test
        fun `returns 200 with null averages when no logs exist`() {
            mockMvc.perform(
                get("/api/v1/sleep-stats")
                    .header("X-User-Id", userId)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.dateFrom").value("2024-05-17T04:00:00Z"))
                .andExpect(jsonPath("$.dateTo").value("2024-06-16T04:00:00Z"))
                .andExpect(jsonPath("$.averageDurationMinutes").doesNotExist())
                .andExpect(jsonPath("$.averageBedTime").doesNotExist())
                .andExpect(jsonPath("$.averageWakeTime").doesNotExist())
                .andExpect(jsonPath("$.moodFrequencies.goodFrequency").value(0))
                .andExpect(jsonPath("$.moodFrequencies.okFrequency").value(0))
                .andExpect(jsonPath("$.moodFrequencies.badFrequency").value(0))
        }

        @Test
        fun `returns 200 with computed averages after creating a log`() {
            val bedTime = todayBedTime()
            val wakeTime = todayWakeTime()

            mockMvc.perform(
                post("/api/v1/sleep-log")
                    .header("X-User-Id", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"bedTime":"$bedTime","wakeTime":"$wakeTime","mood":"GOOD"}""")
            )
                .andExpect(status().isCreated)

            mockMvc.perform(
                get("/api/v1/sleep-stats")
                    .header("X-User-Id", userId)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.averageDurationMinutes").value(495))
                .andExpect(jsonPath("$.moodFrequencies.goodFrequency").value(1))
                .andExpect(jsonPath("$.moodFrequencies.okFrequency").value(0))
                .andExpect(jsonPath("$.moodFrequencies.badFrequency").value(0))
        }

        @Test
        fun `returns correct averages across multiple logs on different days`() {
            // Insert historical logs directly so we don't need to manipulate the clock.
            // Day 1: bed 22:00 -> wake 06:00 = 8h (480m), GOOD
            // Day 2: bed 23:00 -> wake 07:00 = 8h (480m), OK
            // Day 3 (today, via API): bed 22:30 -> wake 06:45 = 8h 15m (495m), GOOD
            val nyZone = ZoneId.of("America/New_York")
            val day1Bed = TODAY.minusDays(3).atTime(22, 0).atOffset(ZoneOffset.UTC)
            val day1Wake = TODAY.minusDays(2).atTime(6, 0).atOffset(ZoneOffset.UTC)
            val day2Bed = TODAY.minusDays(2).atTime(23, 0).atOffset(ZoneOffset.UTC)
            val day2Wake = TODAY.minusDays(1).atTime(7, 0).atOffset(ZoneOffset.UTC)

            sleepLogRepository.saveSleepLog(
                SleepLog(
                    userId = userId,
                    mood = Mood.GOOD,
                    bedTime = day1Bed,
                    bedTimeZone = nyZone,
                    wakeTime = day1Wake,
                    wakeTimeZone = nyZone
                )
            )
            sleepLogRepository.saveSleepLog(
                SleepLog(
                    userId = userId,
                    mood = Mood.OK,
                    bedTime = day2Bed,
                    bedTimeZone = nyZone,
                    wakeTime = day2Wake,
                    wakeTimeZone = nyZone
                )
            )

            val bedTime = todayBedTime()
            val wakeTime = todayWakeTime()
            mockMvc.perform(
                post("/api/v1/sleep-log")
                    .header("X-User-Id", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"bedTime":"$bedTime","wakeTime":"$wakeTime","mood":"GOOD"}""")
            )
                .andExpect(status().isCreated)

            // avg duration = (480 + 480 + 495) / 3 = 485
            mockMvc.perform(
                get("/api/v1/sleep-stats")
                    .header("X-User-Id", userId)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.averageDurationMinutes").value(485))
                .andExpect(jsonPath("$.moodFrequencies.goodFrequency").value(2))
                .andExpect(jsonPath("$.moodFrequencies.okFrequency").value(1))
                .andExpect(jsonPath("$.moodFrequencies.badFrequency").value(0))
        }

        @Test
        fun `does not include another user's logs in averages`() {
            createSleepLogForUser2()

            mockMvc.perform(
                get("/api/v1/sleep-stats")
                    .header("X-User-Id", userId)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.averageDurationMinutes").doesNotExist())
                .andExpect(jsonPath("$.moodFrequencies.badFrequency").value(0))
        }

        @Test
        fun `returns 404 when user does not exist`() {
            mockMvc.perform(
                get("/api/v1/sleep-stats")
                    .header("X-User-Id", 999999L)
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error").value("Not Found"))
        }
    }

    private fun utcDateTime(date: LocalDate, hour: Int, minute: Int): String =
        date.atTime(hour, minute).atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    private fun todayBedTime(): String = utcDateTime(TODAY.minusDays(1), 22, 30)

    private fun todayWakeTime(): String = utcDateTime(TODAY, 6, 45)

    private fun createSleepLogForUser2() {
        val laZone = ZoneId.of("America/Los_Angeles")
        val todayLA = LocalDate.ofInstant(FIXED_INSTANT, laZone)
        val bedTime = todayLA.minusDays(1).atTime(22, 0).atZone(laZone).toOffsetDateTime()
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val wakeTime = todayLA.atTime(6, 0).atZone(laZone).toOffsetDateTime()
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        mockMvc.perform(
            post("/api/v1/sleep-log")
                .header("X-User-Id", user2Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"bedTime":"$bedTime","wakeTime":"$wakeTime","mood":"BAD"}""")
        )
            .andExpect(status().isCreated)
    }
}
