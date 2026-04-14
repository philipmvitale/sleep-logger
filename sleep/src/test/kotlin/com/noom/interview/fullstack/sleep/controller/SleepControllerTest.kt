package com.noom.interview.fullstack.sleep.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.noom.interview.fullstack.sleep.api.model.CreateSleepLogRequest
import com.noom.interview.fullstack.sleep.exception.ResourceConflictException
import com.noom.interview.fullstack.sleep.exception.ResourceNotFoundException
import com.noom.interview.fullstack.sleep.model.Mood
import com.noom.interview.fullstack.sleep.model.NewSleepLog
import com.noom.interview.fullstack.sleep.model.SleepLog
import com.noom.interview.fullstack.sleep.model.SleepStats
import com.noom.interview.fullstack.sleep.service.SleepService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import com.noom.interview.fullstack.sleep.api.model.Mood as ApiMood

@WebMvcTest(SleepController::class)
@ActiveProfiles("unittest")
@Import(SleepControllerTest.Config::class)
class SleepControllerTest {

    @TestConfiguration
    class Config {
        @Bean
        fun sleepService(): SleepService = mockk()
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var sleepService: SleepService

    private val userId = 42L

    @Nested
    inner class CreateSleepLog {

        @Test
        fun `returns 201 with sleep log response`() {
            val request = CreateSleepLogRequest(
                bedTime = OffsetDateTime.of(2024, 1, 14, 22, 30, 0, 0, ZoneOffset.UTC),
                wakeTime = OffsetDateTime.of(2024, 1, 15, 6, 45, 0, 0, ZoneOffset.UTC),
                mood = ApiMood.GOOD
            )
            val sleepLog = buildSleepLog()
            val captured = slot<NewSleepLog>()

            every { sleepService.createTodaySleepLog(userId, capture(captured)) } returns sleepLog

            mockMvc.perform(
                post("/api/v1/sleep-log")
                    .header("X-User-Id", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.bedTime").value("2024-01-14T22:30:00Z"))
                .andExpect(jsonPath("$.wakeTime").value("2024-01-15T06:45:00Z"))
                .andExpect(jsonPath("$.durationMinutes").value(495))
                .andExpect(jsonPath("$.mood").value("GOOD"))

            assertThat(captured.captured.bedTime).isEqualTo(request.bedTime)
            assertThat(captured.captured.wakeTime).isEqualTo(request.wakeTime)
            assertThat(captured.captured.mood.name).isEqualTo(request.mood.value)
        }

        @Test
        fun `returns 409 when sleep log already exists`() {
            val request = CreateSleepLogRequest(
                bedTime = OffsetDateTime.of(2024, 1, 14, 22, 30, 0, 0, ZoneOffset.UTC),
                wakeTime = OffsetDateTime.of(2024, 1, 15, 6, 45, 0, 0, ZoneOffset.UTC),
                mood = ApiMood.GOOD
            )

            every { sleepService.createTodaySleepLog(userId, any<NewSleepLog>()) } throws
                ResourceConflictException("A sleep log already exists for today.")

            mockMvc.perform(
                post("/api/v1/sleep-log")
                    .header("X-User-Id", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("A sleep log already exists for today."))
        }

        @Test
        fun `returns 400 when request body is invalid`() {
            mockMvc.perform(
                post("/api/v1/sleep-log")
                    .header("X-User-Id", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"invalid\": true}")
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("Bad Request"))
        }

        @Test
        fun `returns 400 when X-User-Id header is missing`() {
            val request = CreateSleepLogRequest(
                bedTime = OffsetDateTime.of(2024, 1, 14, 22, 30, 0, 0, ZoneOffset.UTC),
                wakeTime = OffsetDateTime.of(2024, 1, 15, 6, 45, 0, 0, ZoneOffset.UTC),
                mood = ApiMood.GOOD
            )

            mockMvc.perform(
                post("/api/v1/sleep-log")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("Bad Request"))
        }

        @Test
        fun `returns 400 when X-User-Id is zero`() {
            val request = CreateSleepLogRequest(
                bedTime = OffsetDateTime.of(2024, 1, 14, 22, 30, 0, 0, ZoneOffset.UTC),
                wakeTime = OffsetDateTime.of(2024, 1, 15, 6, 45, 0, 0, ZoneOffset.UTC),
                mood = ApiMood.GOOD
            )

            mockMvc.perform(
                post("/api/v1/sleep-log")
                    .header("X-User-Id", 0)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("Bad Request"))
        }

        @Test
        fun `returns 400 when X-User-Id is negative`() {
            val request = CreateSleepLogRequest(
                bedTime = OffsetDateTime.of(2024, 1, 14, 22, 30, 0, 0, ZoneOffset.UTC),
                wakeTime = OffsetDateTime.of(2024, 1, 15, 6, 45, 0, 0, ZoneOffset.UTC),
                mood = ApiMood.GOOD
            )

            mockMvc.perform(
                post("/api/v1/sleep-log")
                    .header("X-User-Id", -1)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("Bad Request"))
        }

        @Test
        fun `returns 400 when mood value is not a valid enum`() {
            mockMvc.perform(
                post("/api/v1/sleep-log")
                    .header("X-User-Id", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"bedTime":"2024-01-14T22:30:00Z","wakeTime":"2024-01-15T06:45:00Z","mood":"AMAZING"}""")
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("Bad Request"))
        }
    }

    @Nested
    inner class GetLastNightSleep {

        @Test
        fun `returns 200 with sleep log response`() {
            val sleepLog = buildSleepLog()

            every { sleepService.getTodaySleepLog(userId) } returns sleepLog

            mockMvc.perform(
                get("/api/v1/sleep-log")
                    .header("X-User-Id", userId)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.durationMinutes").value(495))
                .andExpect(jsonPath("$.mood").value("GOOD"))
        }

        @Test
        fun `returns 404 when no sleep log found`() {
            every { sleepService.getTodaySleepLog(userId) } throws
                ResourceNotFoundException("No sleep log found for today.")

            mockMvc.perform(
                get("/api/v1/sleep-log")
                    .header("X-User-Id", userId)
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("No sleep log found for today."))
        }

        @Test
        fun `returns 400 when X-User-Id header is missing`() {
            mockMvc.perform(
                get("/api/v1/sleep-log")
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("Bad Request"))
        }

        @Test
        fun `returns 400 when X-User-Id is zero`() {
            mockMvc.perform(
                get("/api/v1/sleep-log")
                    .header("X-User-Id", 0)
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("Bad Request"))
        }

        @Test
        fun `returns 400 when X-User-Id is negative`() {
            mockMvc.perform(
                get("/api/v1/sleep-log")
                    .header("X-User-Id", -1)
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("Bad Request"))
        }
    }

    @Nested
    inner class GetSleepAverages {

        @Test
        fun `returns 200 with averages response`() {
            val stats = SleepStats(
                dateFrom = OffsetDateTime.of(2023, 12, 17, 0, 0, 0, 0, ZoneOffset.UTC),
                dateTo = OffsetDateTime.of(2024, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC),
                averageDurationMinutes = 465,
                averageBedTime = LocalTime.of(23, 0),
                averageWakeTime = LocalTime.of(6, 45),
                moodFrequencies = mapOf(Mood.BAD to 5, Mood.OK to 12, Mood.GOOD to 13)
            )

            every { sleepService.getSleepStats(userId) } returns stats

            mockMvc.perform(
                get("/api/v1/sleep-stats")
                    .header("X-User-Id", userId)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.dateFrom").value("2023-12-17T00:00:00Z"))
                .andExpect(jsonPath("$.dateTo").value("2024-01-15T00:00:00Z"))
                .andExpect(jsonPath("$.averageDurationMinutes").value(465))
                .andExpect(jsonPath("$.averageBedTime").value("23:00:00"))
                .andExpect(jsonPath("$.averageWakeTime").value("06:45:00"))
                .andExpect(jsonPath("$.moodFrequencies.badFrequency").value(5))
                .andExpect(jsonPath("$.moodFrequencies.okFrequency").value(12))
                .andExpect(jsonPath("$.moodFrequencies.goodFrequency").value(13))
        }

        @Test
        fun `returns 200 with null averages when no logs exist`() {
            val stats = SleepStats(
                dateFrom = OffsetDateTime.of(2023, 12, 17, 0, 0, 0, 0, ZoneOffset.UTC),
                dateTo = OffsetDateTime.of(2024, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC),
                averageDurationMinutes = null,
                averageBedTime = null,
                averageWakeTime = null,
                moodFrequencies = emptyMap()
            )

            every { sleepService.getSleepStats(userId) } returns stats

            mockMvc.perform(
                get("/api/v1/sleep-stats")
                    .header("X-User-Id", userId)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.averageDurationMinutes").doesNotExist())
                .andExpect(jsonPath("$.averageBedTime").doesNotExist())
                .andExpect(jsonPath("$.averageWakeTime").doesNotExist())
        }

        @Test
        fun `returns 400 when X-User-Id header is missing`() {
            mockMvc.perform(
                get("/api/v1/sleep-stats")
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("Bad Request"))
        }

        @Test
        fun `returns 400 when X-User-Id is zero`() {
            mockMvc.perform(
                get("/api/v1/sleep-stats")
                    .header("X-User-Id", 0)
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("Bad Request"))
        }

        @Test
        fun `returns 400 when X-User-Id is negative`() {
            mockMvc.perform(
                get("/api/v1/sleep-stats")
                    .header("X-User-Id", -1)
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("Bad Request"))
        }

        @Test
        fun `delegates to service with correct user id`() {
            val stats = SleepStats(
                dateFrom = OffsetDateTime.of(2023, 12, 17, 0, 0, 0, 0, ZoneOffset.UTC),
                dateTo = OffsetDateTime.of(2024, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC),
                averageDurationMinutes = null,
                averageBedTime = null,
                averageWakeTime = null,
                moodFrequencies = emptyMap()
            )

            every { sleepService.getSleepStats(99L) } returns stats

            mockMvc.perform(
                get("/api/v1/sleep-stats")
                    .header("X-User-Id", 99L)
            )
                .andExpect(status().isOk)

            verify { sleepService.getSleepStats(99L) }
        }
    }

    private fun buildSleepLog(
        bedTime: OffsetDateTime = OffsetDateTime.of(2024, 1, 14, 22, 30, 0, 0, ZoneOffset.UTC),
        wakeTime: OffsetDateTime = OffsetDateTime.of(2024, 1, 15, 6, 45, 0, 0, ZoneOffset.UTC),
        mood: Mood = Mood.GOOD
    ) = SleepLog(
        id = 1L,
        userId = userId,
        bedTime = bedTime,
        bedTimeZone = ZoneId.of("America/New_York"),
        wakeTime = wakeTime,
        wakeTimeZone = ZoneId.of("America/New_York"),
        mood = mood
    )
}
