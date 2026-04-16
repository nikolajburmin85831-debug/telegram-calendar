package io.github.nadya.assistant.adapter.out.gemini.service;

import io.github.nadya.assistant.adapter.out.gemini.config.GeminiProperties;
import io.github.nadya.assistant.adapter.out.gemini.mapper.GeminiInterpretationMapper;
import io.github.nadya.assistant.domain.common.ChannelType;
import io.github.nadya.assistant.domain.conversation.ConversationState;
import io.github.nadya.assistant.domain.conversation.IncomingUserMessage;
import io.github.nadya.assistant.domain.conversation.ConversationStatus;
import io.github.nadya.assistant.domain.intent.IntentType;
import io.github.nadya.assistant.domain.user.UserContext;
import io.github.nadya.assistant.domain.user.UserIdentity;
import io.github.nadya.assistant.ports.out.IntentInterpretationRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiIntentInterpreterAdapterTest {

    private final GeminiIntentInterpreterAdapter adapter = new GeminiIntentInterpreterAdapter(
            new GeminiProperties("gemini-2.5-flash", "", true),
            new GeminiInterpretationMapper()
    );

    @Test
    void shouldInterpretTypicalReminderWithDateAndTime() {
        var interpretation = adapter.interpret(requestFor("Напомни завтра в 10:00 позвонить маме"));

        assertEquals(IntentType.CREATE_CALENDAR_EVENT, interpretation.intentType());
        assertEquals("позвонить маме", interpretation.assistantIntent().entities().get("title"));
        assertEquals("2026-04-17", interpretation.assistantIntent().entities().get("startDate"));
        assertEquals("10:00", interpretation.assistantIntent().entities().get("startTime"));
        assertEquals("false", interpretation.assistantIntent().entities().get("allDay"));
        assertTrue(interpretation.missingFields().isEmpty());
        assertTrue(interpretation.ambiguityMarkers().isEmpty());
        assertTrue(interpretation.safeToExecute());
    }

    @Test
    void shouldInterpretAllDayReminder() {
        var interpretation = adapter.interpret(requestFor("Напомни завтра весь день оплатить аренду"));

        assertEquals(IntentType.CREATE_CALENDAR_EVENT, interpretation.intentType());
        assertEquals("true", interpretation.assistantIntent().entities().get("allDay"));
        assertEquals("оплатить аренду", interpretation.assistantIntent().entities().get("title"));
        assertTrue(interpretation.missingFields().isEmpty());
        assertTrue(interpretation.safeToExecute());
    }

    @Test
    void shouldReturnClarificationShapeForAmbiguousTime() {
        var interpretation = adapter.interpret(requestFor("Создай встречу с командой завтра вечером"));

        assertEquals(IntentType.CREATE_CALENDAR_EVENT, interpretation.intentType());
        assertEquals("с командой", interpretation.assistantIntent().entities().get("title"));
        assertTrue(interpretation.ambiguityMarkers().contains("time_is_range"));
        assertFalse(interpretation.safeToExecute());
    }

    private IntentInterpretationRequest requestFor(String text) {
        IncomingUserMessage message = new IncomingUserMessage(
                "internal-1",
                "external-1",
                new UserIdentity("telegram-user:42"),
                ChannelType.TELEGRAM,
                "telegram-chat:101",
                text,
                Instant.parse("2026-04-16T20:15:30Z")
        );
        UserContext userContext = UserContext.defaultFor(new UserIdentity("telegram-user:42"), "telegram-chat:101");
        ConversationState conversationState = new ConversationState(
                "telegram-chat:101",
                new UserIdentity("telegram-user:42"),
                ConversationStatus.IDLE,
                null,
                null,
                Instant.parse("2026-04-16T20:15:30Z")
        );
        return new IntentInterpretationRequest(message, userContext, conversationState);
    }
}
