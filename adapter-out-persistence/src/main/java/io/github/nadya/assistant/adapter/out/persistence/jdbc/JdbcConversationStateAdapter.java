package io.github.nadya.assistant.adapter.out.persistence.jdbc;

import io.github.nadya.assistant.domain.conversation.ConversationState;
import io.github.nadya.assistant.ports.out.ConversationStatePort;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public final class JdbcConversationStateAdapter implements ConversationStatePort {

    private final JdbcPersistenceSupport support;
    private final PersistenceJsonMapper jsonMapper;

    public JdbcConversationStateAdapter(
            DataSource dataSource,
            JdbcPersistenceSettings settings,
            PersistenceJsonMapper jsonMapper
    ) {
        this.support = new JdbcPersistenceSupport(dataSource, settings.schema());
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Optional<ConversationState> findByConversationId(String conversationId) {
        String sql = "SELECT payload FROM " + support.table("conversation_state") + " WHERE conversation_id = ?";
        try (Connection connection = support.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, conversationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(jsonMapper.read(resultSet.getString("payload"), ConversationState.class));
            }
        } catch (SQLException exception) {
            throw support.operationFailed("Conversation state lookup", exception);
        }
    }

    @Override
    public ConversationState save(ConversationState conversationState) {
        String payload = jsonMapper.write(conversationState);
        long updatedAtMs = conversationState.updatedAt().toEpochMilli();
        String updateSql = """
                UPDATE %s
                SET payload = ?, updated_at_ms = ?
                WHERE conversation_id = ?
                """.formatted(support.table("conversation_state"));
        String insertSql = """
                INSERT INTO %s (conversation_id, payload, updated_at_ms)
                VALUES (?, ?, ?)
                """.formatted(support.table("conversation_state"));

        try (Connection connection = support.openConnection()) {
            if (updateExisting(connection, updateSql, conversationState.conversationId(), payload, updatedAtMs) == 0) {
                insertNew(connection, insertSql, conversationState.conversationId(), payload, updatedAtMs);
            }
            return conversationState;
        } catch (SQLException exception) {
            throw support.operationFailed("Conversation state save", exception);
        }
    }

    private int updateExisting(Connection connection, String updateSql, String conversationId, String payload, long updatedAtMs)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
            statement.setString(1, payload);
            statement.setLong(2, updatedAtMs);
            statement.setString(3, conversationId);
            return statement.executeUpdate();
        }
    }

    private void insertNew(Connection connection, String insertSql, String conversationId, String payload, long updatedAtMs)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
            statement.setString(1, conversationId);
            statement.setString(2, payload);
            statement.setLong(3, updatedAtMs);
            statement.executeUpdate();
        }
    }
}
