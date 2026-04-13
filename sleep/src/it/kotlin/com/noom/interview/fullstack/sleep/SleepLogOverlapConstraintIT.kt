package com.noom.interview.fullstack.sleep

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.SpykBean
import com.noom.interview.fullstack.sleep.model.Mood
import com.noom.interview.fullstack.sleep.model.SleepLog
import com.noom.interview.fullstack.sleep.model.User
import com.noom.interview.fullstack.sleep.repository.SleepLogRepository
import com.noom.interview.fullstack.sleep.repository.UserRepository
import io.mockk.every
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Verifies that the PostgreSQL exclusion constraint `no_overlapping_sleep` fires on
 * overlapping inserts and is translated to a 409 Conflict response. This simulates a
 * concurrent-insert race where the service-layer overlap check passes (because it hasn't
 * seen the first row yet) but the DB constraint catches the conflict.
 */
class SleepLogOverlapConstraintIT : AbstractIntegrationTest() {

    companion object {
        val FIXED_INSTANT: Instant = Instant.parse("2024-06-15T12:00:00Z")
        val TODAY: LocalDate = LocalDate.ofInstant(FIXED_INSTANT, ZoneOffset.UTC)
    }

    @MockkBean
    private lateinit var clock: Clock

    @SpykBean
    private lateinit var sleepLogRepository: SleepLogRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var jdbcTemplate: NamedParameterJdbcTemplate

    private var userId = 0L

    @BeforeEach
    fun setUp() {
        every { clock.instant() } returns FIXED_INSTANT
        every { clock.zone } returns ZoneOffset.UTC
        every { clock.withZone(any()) } answers {
            Clock.fixed(FIXED_INSTANT, firstArg<ZoneId>())
        }

        jdbcTemplate.jdbcTemplate.execute("TRUNCATE sleep_logs, users CASCADE")

        userId = userRepository.saveUser(User(id = 0, timeZone = ZoneId.of("America/New_York"))).id
    }

    @Test
    fun `returns 409 when DB exclusion constraint catches overlapping sleep log`() {
        val nyZone = ZoneId.of("America/New_York")
        val bedTime = TODAY.minusDays(1).atTime(22, 0).atOffset(ZoneOffset.UTC)
        val wakeTime = TODAY.atTime(6, 0).atOffset(ZoneOffset.UTC)

        // Insert a log directly via the repository, simulating a concurrent write.
        sleepLogRepository.saveSleepLog(
            SleepLog(
                userId = userId,
                mood = Mood.GOOD,
                bedTime = bedTime,
                bedTimeZone = nyZone,
                wakeTime = wakeTime,
                wakeTimeZone = nyZone
            )
        )

        // Make the service-layer overlap check see no existing log, simulating the
        // read-before-write race window in a concurrent request.
        every { sleepLogRepository.findLatestSleepLogByUserId(userId) } returns null

        // POST an overlapping sleep log — the service check passes, but the DB
        // exclusion constraint rejects the insert.
        val overlappingBed = TODAY.minusDays(1).atTime(23, 0).atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val overlappingWake = TODAY.atTime(7, 0).atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        mockMvc.perform(
            post("/api/v1/sleep-log")
                .header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"bedTime":"$overlappingBed","wakeTime":"$overlappingWake","mood":"OK"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("Conflict"))
            .andExpect(jsonPath("$.message").value("This sleep log overlaps with an existing entry."))
    }
}
