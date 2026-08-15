package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

// Java, not SQL: a SQL migration's checksum would bake this secret's hash into flyway_schema_history.
public class V3__BootstrapAdmin extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        String username = System.getenv().getOrDefault("ADAGS_BOOTSTRAP_ADMIN_USERNAME", "admin");
        String password = System.getenv("ADAGS_BOOTSTRAP_ADMIN_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "ADAGS_BOOTSTRAP_ADMIN_PASSWORD is not set. The bootstrap admin is "
                            + "created from an install-time secret, not a default password.");
        }

        Argon2PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        String passwordHash = encoder.encode(password);

        Connection connection = context.getConnection();
        UUID userId = insertBootstrapUser(connection, username, passwordHash);
        UUID adminRoleId = findAdminRoleId(connection);
        assignGlobalAdminRole(connection, userId, adminRoleId);
    }

    private UUID insertBootstrapUser(Connection connection, String username, String passwordHash) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO app_user (username, password_hash, status, must_change_password) "
                        + "VALUES (?, ?, 'ACTIVE', TRUE) RETURNING id")) {
            statement.setString(1, username);
            statement.setString(2, passwordHash);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return (UUID) resultSet.getObject("id");
            }
        }
    }

    private UUID findAdminRoleId(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM role WHERE code = 'ADMIN'");
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new IllegalStateException("ADMIN role not found — V2 must run before V3.");
            }
            return (UUID) resultSet.getObject("id");
        }
    }

    private void assignGlobalAdminRole(Connection connection, UUID userId, UUID adminRoleId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO role_assignment (user_id, role_id, scope_type, scope_id) "
                        + "VALUES (?, ?, 'GLOBAL', NULL)")) {
            statement.setObject(1, userId);
            statement.setObject(2, adminRoleId);
            statement.executeUpdate();
        }
    }
}
