package io.github.nadya.assistant.application.service;

import io.github.nadya.assistant.application.handler.ClarificationHandler;
import io.github.nadya.assistant.application.handler.CreateCalendarEventHandler;
import io.github.nadya.assistant.application.handler.PendingConfirmationHandler;
import io.github.nadya.assistant.application.orchestration.IntentRoutingService;
import io.github.nadya.assistant.domain.calendar.CalendarEventDraft;
import io.github.nadya.assistant.domain.calendar.CalendarEventReference;
import io.github.nadya.assistant.domain.common.ChannelType;
import io.github.nadya.assistant.domain.common.ConfidenceScore;
import io.github.nadya.assistant.domain.conversation.ConversationState;
import io.github.nadya.assistant.domain.conversation.ConversationStatus;
import io.github.nadya.assistant.domain.conversation.IncomingUserMessage;
import io.github.nadya.assistant.domain.execution.ExecutionResult;
import io.github.nadya.assistant.domain.intent.AssistantIntent;
import io.github.nadya.assistant.domain.intent.IntentInterpretation;
import io.github.nadya.assistant.domain.intent.IntentType;
import io.github.nadya.assistant.domain.user.UserContext;
import io.github.nadya.assistant.domain.user.UserIdentity;
import io.github.nadya.assistant.ports.out.AuditEntry;
import io.github.nadya.assistant.ports.out.AuditPort;
import io.github.nadya.assistant.ports.out.CalendarPort;
import io.github.nadya.assistant.ports.out.ConversationStatePort;
import io.github.nadya.assistant.ports.out.IdempotencyPort;
import io.github.nadya.assistant.ports.out.IntentInterpretationRequest;
import io.github.nadya.assistant.ports.out.IntentInterpreterPort;
import io.github.nadya.assistant.ports.out.NotificationCommand;
import io.github.nadya.assistant.ports.out.NotificationPort;
import io.github.nadya.assistant.ports.out.UserContextPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandleIncomingMessageServiceTest {

    @Test
    void shouldCreateCalendarEventAndNotifyUserOnSuccessPath() {
        RecordingCalendarPort calendarPort = new RecordingCalendarPort();
        RecordingNotificationPort notificationPort = new RecordingNotificationPort();
        InMemoryConversationStatePort conversationStatePort = new InMemoryConversationStatePort();
        InMemoryUserContextPort userContextPort = new InMemoryUserContextPort();

        HandleIncomingMessageService service = createService(
                buildInterpretation(
                        Map.of(
                                "title", "позвонить маме",
                                "startDate", "2026-04-17",
                                "startTime", "10:00",
                                "allDay", "false"
                        ),
                        List.of(),
                        List.of(),
                        true,
                        0.95d
                ),
                userContextPort,
                conversationStatePort,
                new InMemoryIdempotencyPort(),
                calendarPort,
                notificationPort,
                new RecordingAuditPort()
        );

        ExecutionResult result = service.handle(sampleMessage("msg-1", "Напомни завтра в 10:00 позвонить маме"));

        assertTrue(result.success());
        assertNotNull(result.createdResourceReference());
        assertTrue(result.userSummary().contains("позвонить маме"));
        assertEquals(1, calendarPort.createdDrafts.size());
        assertEquals("позвонить маме", calendarPort.createdDrafts.get(0).title());
        assertFalse(calendarPort.createdDrafts.get(0).allDay());
        assertEquals(1, notificationPort.commands.size());
        assertEquals(ConversationStatus.COMPLETED, conversationStatePort.findByConversationId("telegram-chat:101").orElseThrow().status());
        assertEquals("telegram-chat:101", userContextPort.findByUserId(new UserIdentity("telegram-user:42")).orElseThrow().activeConversationId());
    }

    @Test
    void shouldAskForClarificationWhenTimeIsMissing() {
        RecordingCalendarPort calendarPort = new RecordingCalendarPort();
        RecordingNotificationPort notificationPort = new RecordingNotificationPort();
        InMemoryConversationStatePort conversationStatePort = new InMemoryConversationStatePort();

        HandleIncomingMessageService service = createService(
                buildInterpretation(
                        Map.of(
                                "title", "встреча с командой",
                                "startDate", "2026-04-17",
                                "allDay", "false"
                        ),
                        List.of(),
                        List.of("time"),
                        false,
                        0.70d
                ),
                new InMemoryUserContextPort(),
                conversationStatePort,
                new InMemoryIdempotencyPort(),
                calendarPort,
                notificationPort,
                new RecordingAuditPort()
        );

        ExecutionResult result = service.handle(sampleMessage("msg-2", "Создай встречу с командой завтра"));

        assertTrue(result.success());
        assertNull(result.createdResourceReference());
        assertEquals("Во сколько должно начаться событие?", result.userSummary());
        assertTrue(calendarPort.createdDrafts.isEmpty());
        assertEquals(1, notificationPort.commands.size());
        assertEquals(ConversationStatus.AWAITING_TIME, conversationStatePort.findByConversationId("telegram-chat:101").orElseThrow().status());
    }

    @Test
    void shouldSkipDuplicateMessageByIdempotencyKey() {
        RecordingCalendarPort calendarPort = new RecordingCalendarPort();
        RecordingNotificationPort notificationPort = new RecordingNotificationPort();

        HandleIncomingMessageService service = createService(
                buildInterpretation(
                        Map.of(
                                "title", "позвонить маме",
                                "startDate", "2026-04-17",
                                "startTime", "10:00",
                                "allDay", "false"
                        ),
                        List.of(),
                        List.of(),
                        true,
                        0.95d
                ),
                new InMemoryUserContextPort(),
                new InMemoryConversationStatePort(),
                new InMemoryIdempotencyPort(),
                calendarPort,
                notificationPort,
                new RecordingAuditPort()
        );

        IncomingUserMessage message = sampleMessage("msg-3", "Напомни завтра в 10:00 позвонить маме");

        ExecutionResult first = service.handle(message);
        ExecutionResult second = service.handle(message);

        assertTrue(first.success());
        assertTrue(second.success());
        assertEquals("duplicate_message_skipped", second.auditDetails());
        assertEquals(1, calendarPort.createdDrafts.size());
        assertEquals(1, notificationPort.commands.size());
    }

    private HandleIncomingMessageService createService(
            IntentInterpretation interpretation,
            UserContextPort userContextPort,
            ConversationStatePort conversationStatePort,
            IdempotencyPort idempotencyPort,
            CalendarPort calendarPort,
            NotificationPort notificationPort,
            AuditPort auditPort
    ) {
        return new HandleIncomingMessageService(
                userContextPort,
                conversationStatePort,
                idempotencyPort,
                new FixedIntentInterpreterPort(interpretation),
                notificationPort,
                auditPort,
                new IntentRoutingService(new ConfirmationPolicyService()),
                new CreateCalendarEventHandler(calendarPort),
                new ClarificationHandler(),
                new PendingConfirmationHandler()
        );
    }

    private IntentInterpretation buildInterpretation(
            Map<String, String> entities,
            List<String> ambiguityMarkers,
            List<String> missingFields,
            boolean safeToExecute,
            double confidence
    ) {
        return new IntentInterpretation(
                new AssistantIntent(IntentType.CREATE_CALENDAR_EVENT, entities),
                new ConfidenceScore(confidence),
                ambiguityMarkers,
                missingFields,
                safeToExecute
        );
    }

    private IncomingUserMessage sampleMessage(String externalMessageId, String text) {
        return new IncomingUserMessage(
                "internal-" + externalMessageId,
                externalMessageId,
                new UserIdentity("telegram-user:42"),
                ChannelType.TELEGRAM,
                "telegram-chat:101",
                text,
                Instant.parse("2026-04-16T20:15:30Z")
        );
    }

    private static final class FixedIntentInterpreterPort implements IntentInterpreterPort {
        private final IntentInterpretation interpretation;

        private FixedIntentInterpreterPort(IntentInterpretation interpretation) {
            this.interpretation = interpretation;
        }

        @Override
        public IntentInterpretation interpret(IntentInterpretationRequest request) {
            return interpretation;
        }
    }

    private static final class RecordingCalendarPort implements CalendarPort {
        private final List<CalendarEventDraft> createdDrafts = new ArrayList<>();

        @Override
        public CalendarEventReference createEvent(CalendarEventDraft draft) {
            createdDrafts.add(draft);
            return new CalendarEventReference("calendar-ref-" + createdDrafts.size(), "stub://" + createdDrafts.size());
        }
    }

    private static final class RecordingNotificationPort implements NotificationPort {
        private final List<NotificationCommand> commands = new ArrayList<>();

        @Override
        public void send(NotificationCommand command) {
            commands.add(command);
        }
    }

    private static final class RecordingAuditPort implements AuditPort {
        private final List<AuditEntry> entries = new ArrayList<>();

        @Override
        public void record(AuditEntry entry) {
            entries.add(entry);
        }
    }

    private static final class InMemoryUserContextPort implements UserContextPort {
        private final Map<String, UserContext> storage = new HashMap<>();

        @Override
        public Optional<UserContext> findByUserId(UserIdentity userId) {
            return Optional.ofNullable(storage.get(userId.value()));
        }

        @Override
        public UserContext save(UserContext userContext) {
            storage.put(userContext.userId().value(), userContext);
            return userContext;
        }
    }

    private static final class InMemoryConversationStatePort implements ConversationStatePort {
        private final Map<String, ConversationState> storage = new HashMap<>();

        @Override
        public Optional<ConversationState> findByConversationId(String conversationId) {
            return Optional.ofNullable(storage.get(conversationId));
        }

        @Override
        public ConversationState save(ConversationState conversationState) {
            storage.put(conversationState.conversationId(), conversationState);
            return conversationState;
        }
    }

    private static final class InMemoryIdempotencyPort implements IdempotencyPort {
        private final Set<String> keys = new HashSet<>();

        @Override
        public boolean registerIfAbsent(String key) {
            return keys.add(key);
        }
    }
}
