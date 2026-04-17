package io.github.nadya.assistant.adapter.out.persistence.jdbc;

import io.github.nadya.assistant.domain.common.ChannelType;
import io.github.nadya.assistant.domain.common.ConfidenceScore;
import io.github.nadya.assistant.domain.common.Timezone;
import io.github.nadya.assistant.domain.conversation.ClarificationRequest;
import io.github.nadya.assistant.domain.conversation.ConversationState;
import io.github.nadya.assistant.domain.conversation.ConversationStatus;
import io.github.nadya.assistant.domain.conversation.IncomingUserMessage;
import io.github.nadya.assistant.domain.conversation.PendingAction;
import io.github.nadya.assistant.domain.intent.AssistantIntent;
import io.github.nadya.assistant.domain.intent.IntentInterpretation;
import io.github.nadya.assistant.domain.intent.IntentType;
import io.github.nadya.assistant.domain.user.ConfirmationPreference;
import io.github.nadya.assistant.domain.user.UserContext;
import io.github.nadya.assistant.domain.user.UserIdentity;
import io.github.nadya.assistant.ports.out.AuditEntry;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcPersistenceAdaptersTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldPersistStateAcrossAdapterRecreation() {
        String databaseName = tempDir.resolve("assistant-state").toString();
        JdbcPersistenceSettings settings = new JdbcPersistenceSettings("assistant", Duration.ofHours(24));
        PersistenceJsonMapper jsonMapper = new PersistenceJsonMapper();

        DataSource firstDataSource = dataSource(databaseName);
        new JdbcPersistenceSchemaInitializer(firstDataSource, settings).initialize();

        JdbcUserContextAdapter firstUserContextAdapter = new JdbcUserContextAdapter(firstDataSource, settings, jsonMapper);
        JdbcConversationStateAdapter firstConversationStateAdapter = new JdbcConversationStateAdapter(firstDataSource, settings, jsonMapper);
        JdbcIdempotencyAdapter firstIdempotencyAdapter = new JdbcIdempotencyAdapter(firstDataSource, settings);
        JdbcAuditAdapter firstAuditAdapter = new JdbcAuditAdapter(firstDataSource, settings);

        UserContext userContext = new UserContext(
                new UserIdentity("telegram-user:42"),
                new Timezone("Europe/Moscow"),
                "ru",
                ConfirmationPreference.REQUIRE_CONFIRMATION,
                Duration.ofMinutes(30),
                "telegram-chat:101"
        );
        ConversationState conversationState = pendingConversationState();

        firstUserContextAdapter.save(userContext);
        firstConversationStateAdapter.save(conversationState);
        assertTrue(firstIdempotencyAdapter.registerIfAbsent("telegram:conversation:message-1"));
        firstAuditAdapter.record(new AuditEntry(
                new UserIdentity("telegram-user:42"),
                "telegram-chat:101",
                "AWAITING_TIME",
                Instant.parse("2026-04-17T08:00:00Z"),
                "state persisted"
        ));

        DataSource secondDataSource = dataSource(databaseName);
        new JdbcPersistenceSchemaInitializer(secondDataSource, settings).initialize();

        JdbcUserContextAdapter secondUserContextAdapter = new JdbcUserContextAdapter(secondDataSource, settings, jsonMapper);
        JdbcConversationStateAdapter secondConversationStateAdapter = new JdbcConversationStateAdapter(secondDataSource, settings, jsonMapper);
        JdbcIdempotencyAdapter secondIdempotencyAdapter = new JdbcIdempotencyAdapter(secondDataSource, settings);

        UserContext reloadedUserContext = secondUserContextAdapter.findByUserId(new UserIdentity("telegram-user:42")).orElseThrow();
        ConversationState reloadedConversationState = secondConversationStateAdapter.findByConversationId("telegram-chat:101").orElseThrow();

        assertEquals("telegram-chat:101", reloadedUserContext.activeConversationId());
        assertEquals(ConfirmationPreference.REQUIRE_CONFIRMATION, reloadedUserContext.confirmationPreference());
        assertEquals(ConversationStatus.AWAITING_TIME, reloadedConversationState.status());
        assertNotNull(reloadedConversationState.pendingAction());
        assertEquals(
                "позвонить маме",
                reloadedConversationState.pendingAction().interpretation().assistantIntent().entities().get("title")
        );
        assertFalse(secondIdempotencyAdapter.registerIfAbsent("telegram:conversation:message-1"));
        assertEquals(1, auditCount(secondDataSource, "assistant"));
    }

    @Test
    void shouldPruneExpiredIdempotencyMarkersBeforeInsert() {
        JdbcPersistenceSettings settings = new JdbcPersistenceSettings("assistant", Duration.ofSeconds(1));
        DataSource dataSource = dataSource(tempDir.resolve("assistant-idempotency").toString());
        new JdbcPersistenceSchemaInitializer(dataSource, settings).initialize();

        JdbcIdempotencyAdapter adapter = new JdbcIdempotencyAdapter(dataSource, settings);

        assertTrue(adapter.registerIfAbsent("key-1"));
        sleep(1200L);
        assertTrue(adapter.registerIfAbsent("key-1"));
    }

    private ConversationState pendingConversationState() {
        IncomingUserMessage sourceMessage = new IncomingUserMessage(
                "internal-1",
                "external-1",
                new UserIdentity("telegram-user:42"),
                ChannelType.TELEGRAM,
                "telegram-chat:101",
                "Напомни завтра позвонить маме",
                Instant.parse("2026-04-16T20:15:30Z")
        );
        PendingAction pendingAction = new PendingAction(
                "pending-internal-1",
                sourceMessage,
                new IntentInterpretation(
                        new AssistantIntent(
                                IntentType.CREATE_CALENDAR_EVENT,
                                Map.of(
                                        "title", "позвонить маме",
                                        "startDate", "2026-04-17",
                                        "allDay", "false"
                                )
                        ),
                        new ConfidenceScore(0.72d),
                        List.of(),
                        List.of("time"),
                        false
                ),
                Instant.parse("2026-04-16T20:15:30Z")
        );
        return new ConversationState(
                "telegram-chat:101",
                new UserIdentity("telegram-user:42"),
                ConversationStatus.AWAITING_TIME,
                new ClarificationRequest("time", List.of("time"), "Во сколько должно начаться событие?", pendingAction.pendingActionId()),
                null,
                pendingAction,
                Instant.parse("2026-04-16T20:15:30Z")
        );
    }

    private DataSource dataSource(String databaseName) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:file:" + databaseName.replace('\\', '/') + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private int auditCount(DataSource dataSource, String schema) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM " + schema + ".audit_entry"
             );
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        } catch (SQLException exception) {
            throw new IllegalStateException("Audit count query failed", exception);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Sleep interrupted", exception);
        }
    }
}
