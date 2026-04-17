package io.github.nadya.assistant.adapter.out.persistence.jdbc;

import io.github.nadya.assistant.ports.out.AuditEntry;
import io.github.nadya.assistant.ports.out.AuditPort;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class JdbcAuditAdapter implements AuditPort {

    private final JdbcPersistenceSupport support;

    public JdbcAuditAdapter(DataSource dataSource, JdbcPersistenceSettings settings) {
        this.support = new JdbcPersistenceSupport(dataSource, settings.schema());
    }

    @Override
    public void record(AuditEntry entry) {
        String sql = """
                INSERT INTO %s (user_id, conversation_id, event_type, occurred_at_ms, details)
                VALUES (?, ?, ?, ?, ?)
                """.formatted(support.table("audit_entry"));

        try (Connection connection = support.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entry.userId().value());
            statement.setString(2, entry.conversationId());
            statement.setString(3, entry.eventType());
            statement.setLong(4, entry.occurredAt().toEpochMilli());
            statement.setString(5, entry.details());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw support.operationFailed("Audit record insert", exception);
        }
    }
}
