package io.github.nadya.assistant.adapter.out.persistence.memory;

import io.github.nadya.assistant.domain.common.Timezone;
import io.github.nadya.assistant.domain.conversation.ConversationState;
import io.github.nadya.assistant.domain.conversation.ConversationStatus;
import io.github.nadya.assistant.domain.user.ConfirmationPreference;
import io.github.nadya.assistant.domain.user.UserContext;
import io.github.nadya.assistant.domain.user.UserIdentity;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void shouldSaveAndLoadConversationState() {
        InMemoryConversationStateAdapter adapter = new InMemoryConversationStateAdapter();
        ConversationState state = new ConversationState(
                "telegram-chat:101",
                new UserIdentity("telegram-user:42"),
                ConversationStatus.AWAITING_TIME,
                null,
                null,
                java.time.Instant.parse("2026-04-16T20:15:30Z")
        );

        adapter.save(state);

        assertEquals(ConversationStatus.AWAITING_TIME, adapter.findByConversationId("telegram-chat:101").orElseThrow().status());
    }

    @Test
    void shouldRegisterIdempotencyOnlyOnce() {
        InMemoryIdempotencyAdapter adapter = new InMemoryIdempotencyAdapter();

        assertTrue(adapter.registerIfAbsent("telegram:123"));
        assertFalse(adapter.registerIfAbsent("telegram:123"));
    }
}
