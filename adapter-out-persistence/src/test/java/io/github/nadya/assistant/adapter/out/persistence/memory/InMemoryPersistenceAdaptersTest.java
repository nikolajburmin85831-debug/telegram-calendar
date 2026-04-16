package io.github.nadya.assistant.adapter.out.persistence.memory;

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
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryPersistenceAdaptersTest {

    @Test
    void shouldSaveAndLoadUserContext() {
        InMemoryUserContextAdapter adapter = new InMemoryUserContextAdapter();
        UserContext context = new UserContext(
                new UserIdentity("telegram-user:42"),
                new Timezone("Europe/Moscow"),
                "ru",
                ConfirmationPreference.AUTO_EXECUTE,
                Duration.ofHours(1),
                "telegram-chat:101"
        );

        adapter.save(context);

        assertEquals("telegram-chat:101", adapter.findByUserId(new UserIdentity("telegram-user:42")).orElseThrow().activeConversationId());
    }

    @Test
    void shouldSaveAndLoadConversationStateWithPendingAction() {
        InMemoryConversationStateAdapter adapter = new InMemoryConversationStateAdapter();
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
        ConversationState state = new ConversationState(
                "telegram-chat:101",
                new UserIdentity("telegram-user:42"),
                ConversationStatus.AWAITING_TIME,
                new ClarificationRequest("time", List.of("time"), "Во сколько должно начаться событие?", pendingAction.pendingActionId()),
                null,
                pendingAction,
                Instant.parse("2026-04-16T20:15:30Z")
        );

        adapter.save(state);

        ConversationState loadedState = adapter.findByConversationId("telegram-chat:101").orElseThrow();
        assertEquals(ConversationStatus.AWAITING_TIME, loadedState.status());
        assertNotNull(loadedState.pendingAction());
        assertEquals("позвонить маме", loadedState.pendingAction().interpretation().assistantIntent().entities().get("title"));
    }

    @Test
    void shouldRegisterIdempotencyOnlyOnce() {
        InMemoryIdempotencyAdapter adapter = new InMemoryIdempotencyAdapter();

        assertTrue(adapter.registerIfAbsent("telegram:123"));
        assertFalse(adapter.registerIfAbsent("telegram:123"));
    }
}
