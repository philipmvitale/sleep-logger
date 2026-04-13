package com.noom.interview.fullstack.sleep.repository

import com.noom.interview.fullstack.sleep.model.User
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.ResultSet
import java.time.ZoneId

class JdbcUserRepositoryTest {

    private val jdbcTemplate: NamedParameterJdbcTemplate = mockk()
    private lateinit var repository: JdbcUserRepository

    @BeforeEach
    fun setUp() {
        repository = JdbcUserRepository(jdbcTemplate)
    }

    private fun stubResultSet(timezone: String = "America/New_York"): ResultSet = mockk {
        every { getLong("id") } returns 1L
        every { getString("timezone") } returns timezone
    }

    private fun <T> mockQueryWithRowMapper(rs: ResultSet) {
        every {
            jdbcTemplate.query(any<String>(), any<MapSqlParameterSource>(), any<RowMapper<T>>())
        } answers {
            @Suppress("UNCHECKED_CAST")
            val mapper = thirdArg<RowMapper<T>>()
            listOf(mapper.mapRow(rs, 0))
        }
    }

    private fun <T> mockQueryForObjectWithRowMapper(rs: ResultSet) {
        every {
            jdbcTemplate.queryForObject(any<String>(), any<MapSqlParameterSource>(), any<RowMapper<T>>())
        } answers {
            @Suppress("UNCHECKED_CAST")
            val mapper = thirdArg<RowMapper<T>>()
            mapper.mapRow(rs, 0)
        }
    }

    @Nested
    inner class SaveUser {

        @Test
        fun `returns saved user with generated id`() {
            val rs = stubResultSet()
            mockQueryForObjectWithRowMapper<Any>(rs)

            val result = repository.saveUser(User(id = 0L, timeZone = ZoneId.of("America/New_York")))

            assertThat(result.id).isEqualTo(1L)
            assertThat(result.timeZone).isEqualTo(ZoneId.of("America/New_York"))
        }

        @Test
        fun `passes timezone parameter`() {
            val paramsSlot = slot<MapSqlParameterSource>()
            val rs = stubResultSet(timezone = "Asia/Tokyo")

            every {
                jdbcTemplate.queryForObject(any<String>(), capture(paramsSlot), any<RowMapper<Any>>())
            } answers {
                @Suppress("UNCHECKED_CAST")
                val mapper = thirdArg<RowMapper<Any>>()
                mapper.mapRow(rs, 0)
            }

            repository.saveUser(User(id = 0L, timeZone = ZoneId.of("Asia/Tokyo")))

            assertThat(paramsSlot.captured.getValue("timezone")).isEqualTo("Asia/Tokyo")
        }

        @Test
        fun `throws when insert returns null`() {
            every {
                jdbcTemplate.queryForObject(any<String>(), any<MapSqlParameterSource>(), any<RowMapper<Any>>())
            } returns null

            assertThatThrownBy {
                repository.saveUser(User(id = 0L, timeZone = ZoneId.of("America/New_York")))
            }.isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("INSERT RETURNING produced no row")
        }
    }

    @Nested
    inner class FindUserById {

        @Test
        fun `maps all fields correctly from result set`() {
            val rs = stubResultSet()
            mockQueryWithRowMapper<Any>(rs)

            val result = repository.findUserById(1L)

            assertThat(result).isNotNull
            assertThat(result!!.id).isEqualTo(1L)
            assertThat(result.timeZone).isEqualTo(ZoneId.of("America/New_York"))
        }

        @Test
        fun `passes correct id parameter`() {
            val paramsSlot = slot<MapSqlParameterSource>()

            every {
                jdbcTemplate.query(any<String>(), capture(paramsSlot), any<RowMapper<Any>>())
            } returns emptyList()

            repository.findUserById(42L)

            assertThat(paramsSlot.captured.getValue("id")).isEqualTo(42L)
        }

        @Test
        fun `returns null when user not found`() {
            every {
                jdbcTemplate.query(any<String>(), any<MapSqlParameterSource>(), any<RowMapper<Any>>())
            } returns emptyList()

            val result = repository.findUserById(99L)

            assertThat(result).isNull()
        }

        @Test
        fun `handles UTC timezone`() {
            val rs = stubResultSet(timezone = "UTC")
            mockQueryWithRowMapper<Any>(rs)

            val result = repository.findUserById(1L)

            assertThat(result!!.timeZone).isEqualTo(ZoneId.of("UTC"))
        }

        @Test
        fun `handles non-UTC timezone`() {
            val rs = stubResultSet(timezone = "Europe/London")
            mockQueryWithRowMapper<Any>(rs)

            val result = repository.findUserById(1L)

            assertThat(result!!.timeZone).isEqualTo(ZoneId.of("Europe/London"))
        }
    }
}
