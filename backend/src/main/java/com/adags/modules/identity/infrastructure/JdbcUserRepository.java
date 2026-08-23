package com.adags.modules.identity.infrastructure;

import com.adags.modules.identity.domain.User;
import com.adags.modules.identity.domain.UserRepository;
import com.adags.modules.identity.domain.UserSummary;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
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

    @Override
    public User insert(String username, String passwordHash, UUID createdBy) {
        String sql = """
                INSERT INTO app_user (username, password_hash, status, must_change_password, created_by)
                VALUES (:username, :passwordHash, 'ACTIVE', TRUE, :createdBy)
                RETURNING id
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("username", username)
                .addValue("passwordHash", passwordHash)
                .addValue("createdBy", createdBy, Types.OTHER);
        UUID id = jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> (UUID) rs.getObject("id"));
        return new User(id, username, passwordHash, "ACTIVE", true, 0, null);
    }

    @Override
    public List<UserSummary> findAllSummaries() {
        return jdbcTemplate.query(
                "SELECT id, username, status, must_change_password, created_at FROM app_user ORDER BY username",
                (rs, rowNum) -> new UserSummary(
                        (UUID) rs.getObject("id"),
                        rs.getString("username"),
                        rs.getString("status"),
                        rs.getBoolean("must_change_password"),
                        rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public void updatePassword(UUID userId, String passwordHash, boolean mustChangePassword) {
        jdbcTemplate.update(
                "UPDATE app_user SET password_hash = :passwordHash, must_change_password = :mustChangePassword WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("passwordHash", passwordHash)
                        .addValue("mustChangePassword", mustChangePassword)
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
