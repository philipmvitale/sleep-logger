package com.noom.interview.fullstack.sleep.repository

import com.noom.interview.fullstack.sleep.model.User
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.ZoneId

private val logger = KotlinLogging.logger {}

/**
 * JDBC-backed implementation of [UserRepository].
 */
@Repository
class JdbcUserRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) : UserRepository {

    /** Maps a `users` row to a [User] domain object. */
    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        User(
            id = rs.getLong("id"),
            timeZone = ZoneId.of(rs.getString("timezone"))
        )
    }

    override fun findUserById(id: Long): User? {
        val sql = "SELECT id, timezone FROM users WHERE id = :id"
        val params = MapSqlParameterSource("id", id)
        val user = jdbcTemplate.query(sql, params, rowMapper).firstOrNull()
        if (user == null) {
            logger.debug { "User not found: id=$id" }
        }
        return user
    }
}
