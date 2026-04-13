package com.noom.interview.fullstack.sleep.repository

import com.noom.interview.fullstack.sleep.model.Mood
import com.noom.interview.fullstack.sleep.model.SleepLog
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class JdbcSleepLogRepositoryTest {

    private val jdbcTemplate: NamedParameterJdbcTemplate = mockk()
    private lateinit var repository: JdbcSleepLogRepository

    private val bedTime = OffsetDateTime.parse("2024-01-14T22:30:00Z")
    private val wakeTime = OffsetDateTime.parse("2024-01-15T06:45:00Z")
    private val rangeFrom = OffsetDateTime.of(2023, 12, 17, 0, 0, 0, 0, ZoneOffset.UTC)
    private val rangeTo = OffsetDateTime.of(2024, 1, 16, 0, 0, 0, 0, ZoneOffset.UTC)

    @BeforeEach
    fun setUp() {
        repository = JdbcSleepLogRepository(jdbcTemplate)
    }

    private fun stubResultSet(
        mood: String = "GOOD",
        bedTz: String = "UTC",
        wakeTz: String = "UTC"
    ): ResultSet = mockk {
        every { getLong("id") } returns 1L
        every { getLong("user_id") } returns 42L
        every { getString("mood") } returns mood
        every { getObject("bed_time", OffsetDateTime::class.java) } returns bedTime
        every { getString("bed_timezone") } returns bedTz
        every { getObject("wake_time", OffsetDateTime::class.java) } returns wakeTime
        every { getString("wake_timezone") } returns wakeTz
    }

    private fun buildSleepLog(
        mood: Mood = Mood.GOOD,
        bedTz: ZoneId = ZoneId.of("UTC"),
        wakeTz: ZoneId = ZoneId.of("UTC")
    ) = SleepLog(
        id = 0,
        userId = 42L,
        mood = mood,
        bedTime = bedTime,
        bedTimeZone = bedTz,
        wakeTime = wakeTime,
        wakeTimeZone = wakeTz
    )

    private fun mockQueryWithRowMapper(rs: ResultSet) {
        every {
            jdbcTemplate.query(any<String>(), any<MapSqlParameterSource>(), any<RowMapper<SleepLog>>())
        } answers {
            val mapper = thirdArg<RowMapper<SleepLog>>()
            listOf(mapper.mapRow(rs, 0))
        }
    }

    private fun mockQueryForObjectWithRowMapper(rs: ResultSet) {
        every {
            jdbcTemplate.queryForObject(any<String>(), any<MapSqlParameterSource>(), any<RowMapper<SleepLog>>())
        } answers {
            val mapper = thirdArg<RowMapper<SleepLog>>()
            mapper.mapRow(rs, 0)
        }
    }

    @Nested
    inner class Insert {

        @Test
        fun `passes correct parameters to SQL`() {
            val sleepLog = buildSleepLog()
            val paramsSlot = slot<MapSqlParameterSource>()

            every {
                jdbcTemplate.queryForObject(any<String>(), capture(paramsSlot), any<RowMapper<SleepLog>>())
            } returns sleepLog.copy(id = 1)

            repository.saveSleepLog(sleepLog)

            val params = paramsSlot.captured
            assertThat(params.getValue("userId")).isEqualTo(42L)
            assertThat(params.getValue("mood")).isEqualTo("GOOD")
            assertThat(params.getValue("bedTime")).isEqualTo(bedTime)
            assertThat(params.getValue("bedTimeZone")).isEqualTo("UTC")
            assertThat(params.getValue("wakeTime")).isEqualTo(wakeTime)
            assertThat(params.getValue("wakeTimeZone")).isEqualTo("UTC")
        }

        @Test
        fun `throws IllegalStateException when RETURNING produces no row`() {
            every {
                jdbcTemplate.queryForObject(any<String>(), any<MapSqlParameterSource>(), any<RowMapper<SleepLog>>())
            } returns null

            assertThatThrownBy { repository.saveSleepLog(buildSleepLog()) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("INSERT RETURNING produced no row")
                .hasMessageContaining("userId=42")
        }

        @Test
        fun `maps RETURNING clause through row mapper`() {
            val rs = stubResultSet()
            mockQueryForObjectWithRowMapper(rs)

            val result = repository.saveSleepLog(buildSleepLog())

            assertThat(result.id).isEqualTo(1L)
            assertThat(result.userId).isEqualTo(42L)
            assertThat(result.mood).isEqualTo(Mood.GOOD)
            assertThat(result.bedTime).isEqualTo(bedTime)
            assertThat(result.wakeTime).isEqualTo(wakeTime)
        }
    }

    @Nested
    inner class FindByUserIdAndWakeTimeRange {

        @Test
        fun `maps all fields correctly from result set`() {
            val rs = stubResultSet()
            mockQueryWithRowMapper(rs)

            val results = repository.findSleepLogsByUserIdAndWakeTimeRange(42L, rangeFrom, rangeTo)

            assertThat(results).hasSize(1)
            val result = results[0]
            assertThat(result.id).isEqualTo(1L)
            assertThat(result.userId).isEqualTo(42L)
            assertThat(result.mood).isEqualTo(Mood.GOOD)
            assertThat(result.bedTime).isEqualTo(bedTime)
            assertThat(result.bedTimeZone).isEqualTo(ZoneId.of("UTC"))
            assertThat(result.wakeTime).isEqualTo(wakeTime)
            assertThat(result.wakeTimeZone).isEqualTo(ZoneId.of("UTC"))
        }

        @Test
        fun `returns empty list when no logs found`() {
            every {
                jdbcTemplate.query(any<String>(), any<MapSqlParameterSource>(), any<RowMapper<SleepLog>>())
            } returns emptyList()

            val results = repository.findSleepLogsByUserIdAndWakeTimeRange(42L, rangeFrom, rangeTo)

            assertThat(results).isEmpty()
        }

        @Test
        fun `passes correct date range parameters`() {
            val paramsSlot = slot<MapSqlParameterSource>()

            every {
                jdbcTemplate.query(any<String>(), capture(paramsSlot), any<RowMapper<SleepLog>>())
            } returns emptyList()

            repository.findSleepLogsByUserIdAndWakeTimeRange(42L, rangeFrom, rangeTo)

            val params = paramsSlot.captured
            assertThat(params.getValue("userId")).isEqualTo(42L)
            assertThat(params.getValue("fromStart")).isEqualTo(rangeFrom)
            assertThat(params.getValue("toEnd")).isEqualTo(rangeTo)
        }
    }

    @Nested
    inner class FindLatest {

        @Test
        fun `returns sleep log when one exists`() {
            val rs = stubResultSet()
            mockQueryWithRowMapper(rs)

            val result = repository.findLatestSleepLogByUserId(42L)

            assertThat(result).isNotNull
            assertThat(result!!.userId).isEqualTo(42L)
            assertThat(result.mood).isEqualTo(Mood.GOOD)
            assertThat(result.bedTime).isEqualTo(bedTime)
            assertThat(result.wakeTime).isEqualTo(wakeTime)
        }

        @Test
        fun `passes correct userId parameter`() {
            val paramsSlot = slot<MapSqlParameterSource>()

            every {
                jdbcTemplate.query(any<String>(), capture(paramsSlot), any<RowMapper<SleepLog>>())
            } returns listOf(buildSleepLog().copy(id = 1))

            repository.findLatestSleepLogByUserId(42L)

            assertThat(paramsSlot.captured.getValue("userId")).isEqualTo(42L)
        }

        @Test
        fun `returns null when no sleep log exists`() {
            every {
                jdbcTemplate.query(any<String>(), any<MapSqlParameterSource>(), any<RowMapper<SleepLog>>())
            } returns emptyList()

            val result = repository.findLatestSleepLogByUserId(99L)

            assertThat(result).isNull()
        }
    }

    @Nested
    inner class NonUtcTimezones {

        @Test
        fun `row mapper handles non-UTC timezone identifiers`() {
            val rs = stubResultSet(bedTz = "America/New_York", wakeTz = "Europe/London")
            mockQueryWithRowMapper(rs)

            val result = repository.findSleepLogsByUserIdAndWakeTimeRange(42L, rangeFrom, rangeTo).first()

            assertThat(result.bedTimeZone).isEqualTo(ZoneId.of("America/New_York"))
            assertThat(result.wakeTimeZone).isEqualTo(ZoneId.of("Europe/London"))
        }

        @Test
        fun `insert passes non-UTC timezone identifiers correctly`() {
            val paramsSlot = slot<MapSqlParameterSource>()

            every {
                jdbcTemplate.queryForObject(any<String>(), capture(paramsSlot), any<RowMapper<SleepLog>>())
            } returns buildSleepLog(
                bedTz = ZoneId.of("America/New_York"),
                wakeTz = ZoneId.of("Europe/London")
            ).copy(id = 1)

            repository.saveSleepLog(
                buildSleepLog(bedTz = ZoneId.of("America/New_York"), wakeTz = ZoneId.of("Europe/London"))
            )

            val params = paramsSlot.captured
            assertThat(params.getValue("bedTimeZone")).isEqualTo("America/New_York")
            assertThat(params.getValue("wakeTimeZone")).isEqualTo("Europe/London")
        }
    }

    @Nested
    inner class MoodMapping {

        @ParameterizedTest
        @EnumSource(Mood::class)
        fun `row mapper handles mood`(mood: Mood) {
            val rs = stubResultSet(mood = mood.name)
            mockQueryWithRowMapper(rs)

            val result = repository.findLatestSleepLogByUserId(42L)

            assertThat(result!!.mood).isEqualTo(mood)
        }
    }
}
