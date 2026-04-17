package io.github.nadya.assistant.adapter.out.persistence.jdbc;

import io.github.nadya.assistant.ports.out.IdempotencyPort;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class JdbcIdempotencyAdapter implements IdempotencyPort {

    private final JdbcPersistenceSupport support;
    private final JdbcPersistenceSettings settings;

    public JdbcIdempotencyAdapter(DataSource dataSource, JdbcPersistenceSettings settings) {
        this.support = new JdbcPersistenceSupport(dataSource, settings.schema());
        this.settings = settings;
    }

    @Override
    public boolean registerIfAbsent(String key) {
        long now = System.currentTimeMillis();
        long cutoff = now - settings.idempotencyTtl().toMillis();
        String cleanupSql = "DELETE FROM " + support.table("idempotency_marker") + " WHERE created_at_ms < ?";
        String insertSql = """
                INSERT INTO %s (idempotency_key, created_at_ms)
                VALUES (?, ?)
                """.formatted(support.table("idempotency_marker"));

        try (Connection connection = support.openConnection()) {
            cleanupExpired(connection, cleanupSql, cutoff);
            try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
                statement.setString(1, key);
                statement.setLong(2, now);
                statement.executeUpdate();
                return true;
            } catch (SQLException exception) {
                if (support.isDuplicateKey(exception)) {
                    return false;
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw support.operationFailed("Idempotency registration", exception);
        }
    }

    private void cleanupExpired(Connection connection, String cleanupSql, long cutoff) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(cleanupSql)) {
            statement.setLong(1, cutoff);
            statement.executeUpdate();
        }
    }
}
