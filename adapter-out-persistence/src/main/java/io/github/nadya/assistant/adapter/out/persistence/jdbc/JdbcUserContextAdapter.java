package io.github.nadya.assistant.adapter.out.persistence.jdbc;

import io.github.nadya.assistant.domain.user.UserContext;
import io.github.nadya.assistant.domain.user.UserIdentity;
import io.github.nadya.assistant.ports.out.UserContextPort;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public final class JdbcUserContextAdapter implements UserContextPort {

    private final JdbcPersistenceSupport support;
    private final PersistenceJsonMapper jsonMapper;

    public JdbcUserContextAdapter(
            DataSource dataSource,
            JdbcPersistenceSettings settings,
            PersistenceJsonMapper jsonMapper
    ) {
        this.support = new JdbcPersistenceSupport(dataSource, settings.schema());
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Optional<UserContext> findByUserId(UserIdentity userId) {
        String sql = "SELECT payload FROM " + support.table("user_context") + " WHERE user_id = ?";
        try (Connection connection = support.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId.value());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(jsonMapper.read(resultSet.getString("payload"), UserContext.class));
            }
        } catch (SQLException exception) {
            throw support.operationFailed("User context lookup", exception);
        }
    }

    @Override
    public UserContext save(UserContext userContext) {
        String payload = jsonMapper.write(userContext);
        long updatedAtMs = System.currentTimeMillis();
        String updateSql = """
                UPDATE %s
                SET payload = ?, updated_at_ms = ?
                WHERE user_id = ?
                """.formatted(support.table("user_context"));
        String insertSql = """
                INSERT INTO %s (user_id, payload, updated_at_ms)
                VALUES (?, ?, ?)
                """.formatted(support.table("user_context"));

        try (Connection connection = support.openConnection()) {
            if (updateExisting(connection, updateSql, userContext.userId().value(), payload, updatedAtMs) == 0) {
                insertNew(connection, insertSql, userContext.userId().value(), payload, updatedAtMs);
            }
            return userContext;
        } catch (SQLException exception) {
            throw support.operationFailed("User context save", exception);
        }
    }

    private int updateExisting(Connection connection, String updateSql, String userId, String payload, long updatedAtMs)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
            statement.setString(1, payload);
            statement.setLong(2, updatedAtMs);
            statement.setString(3, userId);
            return statement.executeUpdate();
        }
    }

    private void insertNew(Connection connection, String insertSql, String userId, String payload, long updatedAtMs)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
            statement.setString(1, userId);
            statement.setString(2, payload);
            statement.setLong(3, updatedAtMs);
            statement.executeUpdate();
        }
    }
}
