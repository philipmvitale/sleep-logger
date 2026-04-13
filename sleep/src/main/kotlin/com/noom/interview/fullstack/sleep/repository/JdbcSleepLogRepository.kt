package com.noom.interview.fullstack.sleep.repository

import com.noom.interview.fullstack.sleep.model.Mood
import com.noom.interview.fullstack.sleep.model.SleepLog
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneId

private val logger = KotlinLogging.logger {}

/**
 * JDBC-backed implementation of [SleepLogRepository].
 *
 * All timestamps are stored and queried as `TIMESTAMP WITH TIME ZONE`; time-zone identifiers
 * (IANA IDs like "America/New_York") are stored separately so local-time statistics can be
 * reconstructed without offset ambiguity.
 */
@Repository
class JdbcSleepLogRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) : SleepLogRepository {

    /** Maps a `sleep_logs` row to a [SleepLog] domain object. */
    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        SleepLog(
            id = rs.getLong("id"),
            userId = rs.getLong("user_id"),
            mood = Mood.valueOf(rs.getString("mood")),
            bedTime = rs.getObject("bed_time", OffsetDateTime::class.java),
            bedTimeZone = ZoneId.of(rs.getString("bed_timezone")),
            wakeTime = rs.getObject("wake_time", OffsetDateTime::class.java),
            wakeTimeZone = ZoneId.of(rs.getString("wake_timezone"))
        )
    }

    override fun saveSleepLog(sleepLog: SleepLog): SleepLog {
        val sql = """
            INSERT INTO sleep_logs (user_id, mood, bed_time, bed_timezone, wake_time, wake_timezone)
            VALUES (:userId, :mood::mood_type, :bedTime, :bedTimeZone, :wakeTime, :wakeTimeZone)
            RETURNING id, user_id, mood, bed_time, bed_timezone, wake_time, wake_timezone
        """.trimIndent()

        val params = MapSqlParameterSource()
            .addValue("userId", sleepLog.userId)
            .addValue("mood", sleepLog.mood.name)
            .addValue("bedTime", sleepLog.bedTime)
            .addValue("bedTimeZone", sleepLog.bedTimeZone.id)
            .addValue("wakeTime", sleepLog.wakeTime)
            .addValue("wakeTimeZone", sleepLog.wakeTimeZone.id)

        val saved = jdbcTemplate.queryForObject(sql, params, rowMapper)
            ?: throw IllegalStateException("INSERT RETURNING produced no row for userId=${sleepLog.userId}")
        logger.debug { "Inserted sleep_log id=${saved.id} for userId=${saved.userId}" }
        return saved
    }

    override fun findLatestSleepLogByUserId(userId: Long): SleepLog? {
        val sql = """
            SELECT id, user_id, mood, bed_time, bed_timezone, wake_time, wake_timezone
            FROM sleep_logs
            WHERE user_id = :userId
            ORDER BY wake_time DESC LIMIT 1
        """.trimIndent()
        val params = MapSqlParameterSource("userId", userId)
        return jdbcTemplate.query(sql, params, rowMapper).firstOrNull()
    }

    override fun findSleepLogsByUserIdAndWakeTimeRange(
        userId: Long,
        from: OffsetDateTime,
        to: OffsetDateTime
    ): List<SleepLog> {
        val sql = """
            SELECT id, user_id, mood, bed_time, bed_timezone, wake_time, wake_timezone
            FROM sleep_logs
            WHERE user_id = :userId
              AND wake_time >= :fromStart
              AND wake_time < :toEnd
            ORDER BY wake_time DESC
        """.trimIndent()

        val params = MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("fromStart", from)
            .addValue("toEnd", to)

        return jdbcTemplate.query(sql, params, rowMapper)
    }
}
