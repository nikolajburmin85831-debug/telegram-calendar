package io.github.nadya.assistant.adapter.out.gemini.service;

import com.sun.net.httpserver.HttpServer;
import io.github.nadya.assistant.adapter.out.gemini.config.GeminiProperties;
import io.github.nadya.assistant.adapter.out.gemini.mapper.GeminiInterpretationMapper;
import io.github.nadya.assistant.domain.common.ChannelType;
import io.github.nadya.assistant.domain.common.ConfidenceScore;
import io.github.nadya.assistant.domain.conversation.ClarificationRequest;
import io.github.nadya.assistant.domain.conversation.ConversationState;
import io.github.nadya.assistant.domain.conversation.ConversationStatus;
import io.github.nadya.assistant.domain.conversation.IncomingUserMessage;
import io.github.nadya.assistant.domain.conversation.PendingAction;
import io.github.nadya.assistant.domain.intent.AssistantIntent;
import io.github.nadya.assistant.domain.intent.IntentInterpretation;
import io.github.nadya.assistant.domain.intent.IntentType;
import io.github.nadya.assistant.domain.user.UserContext;
import io.github.nadya.assistant.domain.user.UserIdentity;
import io.github.nadya.assistant.ports.out.IntentInterpretationRequest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiIntentInterpreterAdapterTest {

    private final GeminiIntentInterpreterAdapter adapter = new GeminiIntentInterpreterAdapter(
            new GeminiProperties("gemini-2.5-flash", "", true, "https://generativelanguage.googleapis.com", true),
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
    void shouldInterpretAppointmentBookingRequestAsCalendarIntent() {
        var interpretation = adapter.interpret(requestFor("Запиши меня к стоматологу на завтра в 14"));

        assertEquals(IntentType.CREATE_CALENDAR_EVENT, interpretation.intentType());
        assertEquals("2026-04-17", interpretation.assistantIntent().entities().get("startDate"));
        assertEquals("14:00", interpretation.assistantIntent().entities().get("startTime"));
        assertEquals("false", interpretation.assistantIntent().entities().get("allDay"));
        assertEquals("к стоматологу", interpretation.assistantIntent().entities().get("title"));
        assertTrue(interpretation.missingFields().isEmpty());
        assertTrue(interpretation.ambiguityMarkers().isEmpty());
        assertTrue(interpretation.safeToExecute());
    }

    @Test
    void shouldTreatShortTaskPhraseAsReminderCandidate() {
        var interpretation = adapter.interpret(requestFor("купи молоко"));

        assertEquals(IntentType.CREATE_CALENDAR_EVENT, interpretation.intentType());
        assertEquals("купи молоко", interpretation.assistantIntent().entities().get("title"));
        assertEquals("false", interpretation.assistantIntent().entities().get("allDay"));
        assertEquals(List.of("date", "time"), interpretation.missingFields());
        assertTrue(interpretation.ambiguityMarkers().isEmpty());
        assertFalse(interpretation.safeToExecute());
    }

    @Test
    void shouldExtractDateFromShortTaskPhraseWhenPresent() {
        var interpretation = adapter.interpret(requestFor("купи молоко завтра"));

        assertEquals(IntentType.CREATE_CALENDAR_EVENT, interpretation.intentType());
        assertEquals("купи молоко", interpretation.assistantIntent().entities().get("title"));
        assertEquals("2026-04-17", interpretation.assistantIntent().entities().get("startDate"));
        assertEquals(List.of("time"), interpretation.missingFields());
        assertFalse(interpretation.safeToExecute());
    }

    @Test
    void shouldReturnClarificationShapeForAmbiguousTime() {
        var interpretation = adapter.interpret(requestFor("Создай встречу с командой завтра вечером"));

        assertEquals(IntentType.CREATE_CALENDAR_EVENT, interpretation.intentType());
        assertEquals("с командой", interpretation.assistantIntent().entities().get("title"));
        assertTrue(interpretation.ambiguityMarkers().contains("time_is_range"));
        assertFalse(interpretation.safeToExecute());
    }

    @Test
    void shouldInterpretFollowUpTimeInClarificationContext() {
        var interpretation = adapter.interpret(requestForFollowUp("в 10:00"));

        assertEquals(IntentType.CREATE_CALENDAR_EVENT, interpretation.intentType());
        assertEquals("10:00", interpretation.assistantIntent().entities().get("startTime"));
        assertEquals("false", interpretation.assistantIntent().entities().get("allDay"));
    }

    @Test
    void shouldInterpretFollowUpDateAndTimeInClarificationContext() {
        var interpretation = adapter.interpret(requestForFollowUp("завтра в 10"));

        assertEquals(IntentType.CREATE_CALENDAR_EVENT, interpretation.intentType());
        assertEquals("2026-04-17", interpretation.assistantIntent().entities().get("startDate"));
        assertEquals("10:00", interpretation.assistantIntent().entities().get("startTime"));
        assertEquals("false", interpretation.assistantIntent().entities().get("allDay"));
    }

    @Test
    void shouldInterpretFollowUpHourWithMeridiemInClarificationContext() {
        var interpretation = adapter.interpret(requestForFollowUp("в 10 утра"));

        assertEquals(IntentType.CREATE_CALENDAR_EVENT, interpretation.intentType());
        assertEquals("10:00", interpretation.assistantIntent().entities().get("startTime"));
        assertEquals("false", interpretation.assistantIntent().entities().get("allDay"));
    }

    @Test
    void shouldUseRealGeminiPathWhenStubModeDisabled() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> requestQuery = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        try {
            server.createContext("/v1beta/models/gemini-test:generateContent", exchange -> {
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                requestQuery.set(exchange.getRequestURI().getQuery());

                String canonicalJson = """
                        {"intentType":"CREATE_CALENDAR_EVENT","entities":{"title":"демо","startDate":"2026-04-17","startTime":"10:00","allDay":"false"},"confidence":0.96,"ambiguityMarkers":[],"missingFields":[],"safeToExecute":true}
                        """.trim();
                byte[] responseBody = (
                        "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\""
                                + escapeJson(canonicalJson)
                                + "\"}]}}]}"
                ).getBytes(StandardCharsets.UTF_8);

                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseBody.length);
                try (var outputStream = exchange.getResponseBody()) {
                    outputStream.write(responseBody);
                }
            });
            server.start();

            GeminiIntentInterpreterAdapter realAdapter = new GeminiIntentInterpreterAdapter(
                    new GeminiProperties(
                            "gemini-test",
                            "test-key",
                            false,
                            "http://127.0.0.1:" + server.getAddress().getPort(),
                            false
                    ),
                    new GeminiInterpretationMapper()
            );

            IntentInterpretation interpretation = realAdapter.interpret(requestFor("Создай демо завтра в 10:00"));

            assertEquals("key=test-key", requestQuery.get());
            assertNotNull(requestBody.get());
            assertTrue(requestBody.get().contains("Создай демо завтра в 10:00"));
            assertEquals(IntentType.CREATE_CALENDAR_EVENT, interpretation.intentType());
            assertEquals("демо", interpretation.assistantIntent().entities().get("title"));
            assertEquals("10:00", interpretation.assistantIntent().entities().get("startTime"));
            assertTrue(interpretation.safeToExecute());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldFallbackToStubWhenRealGeminiPathFailsAndFallbackEnabled() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        try {
            server.createContext("/v1beta/models/gemini-test:generateContent", exchange -> {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
            });
            server.start();

            GeminiIntentInterpreterAdapter fallbackAdapter = new GeminiIntentInterpreterAdapter(
                    new GeminiProperties(
                            "gemini-test",
                            "test-key",
                            false,
                            "http://127.0.0.1:" + server.getAddress().getPort(),
                            true
                    ),
                    new GeminiInterpretationMapper()
            );

            IntentInterpretation interpretation = fallbackAdapter.interpret(
                    requestFor("Напомни завтра в 10:00 позвонить маме")
            );

            assertEquals(IntentType.CREATE_CALENDAR_EVENT, interpretation.intentType());
            assertEquals("10:00", interpretation.assistantIntent().entities().get("startTime"));
            assertTrue(interpretation.safeToExecute());
        } finally {
            server.stop(0);
        }
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
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
                null,
                Instant.parse("2026-04-16T20:15:30Z")
        );
        return new IntentInterpretationRequest(message, userContext, conversationState);
    }

    private IntentInterpretationRequest requestForFollowUp(String text) {
        IncomingUserMessage sourceMessage = new IncomingUserMessage(
                "internal-source",
                "external-source",
                new UserIdentity("telegram-user:42"),
                ChannelType.TELEGRAM,
                "telegram-chat:101",
                "Напомни завтра позвонить маме",
                Instant.parse("2026-04-16T20:10:00Z")
        );
        PendingAction pendingAction = new PendingAction(
                "pending-internal-source",
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
                Instant.parse("2026-04-16T20:10:00Z")
        );
        IncomingUserMessage followUpMessage = new IncomingUserMessage(
                "internal-2",
                "external-2",
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
                ConversationStatus.AWAITING_TIME,
                new ClarificationRequest("time", List.of("time"), "Во сколько должно начаться событие?", pendingAction.pendingActionId()),
                null,
                pendingAction,
                Instant.parse("2026-04-16T20:15:30Z")
        );
        return new IntentInterpretationRequest(followUpMessage, userContext, conversationState);
    }
}
