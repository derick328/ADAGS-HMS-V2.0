package com.adags.modules.identity.infrastructure;

import com.adags.modules.identity.domain.User;
import com.adags.modules.identity.domain.UserRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcUserRepository implements UserRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, username, password_hash, status, must_change_password, failed_attempts, locked_until
            FROM app_user
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcUserRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        List<User> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE username = :username",
                new MapSqlParameterSource("username", username),
                this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public Optional<User> findById(UUID id) {
        List<User> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id",
                new MapSqlParameterSource("id", id),
                this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public void recordLoginSuccess(UUID userId, Instant loginAt) {
        jdbcTemplate.update(
                "UPDATE app_user SET last_login_at = :loginAt, failed_attempts = 0, locked_until = NULL WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("loginAt", Timestamp.from(loginAt))
                        .addValue("id", userId));
    }

    @Override
    public void recordLoginFailure(UUID userId, int failedAttempts, Instant lockedUntil) {
        jdbcTemplate.update(
                "UPDATE app_user SET failed_attempts = :failedAttempts, locked_until = :lockedUntil WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("failedAttempts", failedAttempts)
                        .addValue("lockedUntil", lockedUntil == null ? null : Timestamp.from(lockedUntil))
                        .addValue("id", userId));
    }

    private User mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp lockedUntil = rs.getTimestamp("locked_until");
        return new User(
                (UUID) rs.getObject("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("status"),
                rs.getBoolean("must_change_password"),
                rs.getInt("failed_attempts"),
                lockedUntil == null ? null : lockedUntil.toInstant()
        );
    }
}
