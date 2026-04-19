package io.github.nadya.assistant.application.service;

import io.github.nadya.assistant.application.handler.ClarificationHandler;
import io.github.nadya.assistant.application.handler.CreateCalendarEventHandler;
import io.github.nadya.assistant.application.handler.PendingConfirmationHandler;
import io.github.nadya.assistant.application.orchestration.IntentRoutingService;
import io.github.nadya.assistant.domain.calendar.CalendarEventDraft;
import io.github.nadya.assistant.domain.calendar.CalendarEventReference;
import io.github.nadya.assistant.domain.common.ChannelType;
import io.github.nadya.assistant.domain.common.ConfidenceScore;
import io.github.nadya.assistant.domain.common.Timezone;
import io.github.nadya.assistant.domain.conversation.ConversationState;
import io.github.nadya.assistant.domain.conversation.ConversationStatus;
import io.github.nadya.assistant.domain.conversation.IncomingUserMessage;
import io.github.nadya.assistant.domain.execution.ExecutionResult;
import io.github.nadya.assistant.domain.household.HouseholdMember;
import io.github.nadya.assistant.domain.household.HouseholdNotificationSettings;
import io.github.nadya.assistant.domain.intent.AssistantIntent;
import io.github.nadya.assistant.domain.intent.IntentInterpretation;
import io.github.nadya.assistant.domain.intent.IntentType;
import io.github.nadya.assistant.domain.user.ConfirmationPreference;
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

import java.time.Clock;
import java.time.Duration;
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
                request -> completeInterpretation("позвонить маме", "2026-04-17", "10:00"),
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
                request -> interpretation(
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

        ConversationState state = conversationStatePort.findByConversationId("telegram-chat:101").orElseThrow();
        assertTrue(result.success());
        assertNull(result.createdResourceReference());
        assertEquals("Во сколько должно начаться событие?", result.userSummary());
        assertTrue(calendarPort.createdDrafts.isEmpty());
        assertEquals(1, notificationPort.commands.size());
        assertEquals(ConversationStatus.AWAITING_TIME, state.status());
        assertNotNull(state.pendingAction());
        assertNotNull(state.clarificationRequest());
    }

    @Test
    void shouldResumeClarificationAndCreateEventOnFollowUp() {
        RecordingCalendarPort calendarPort = new RecordingCalendarPort();
        RecordingNotificationPort notificationPort = new RecordingNotificationPort();
        InMemoryConversationStatePort conversationStatePort = new InMemoryConversationStatePort();

        HandleIncomingMessageService service = createService(
                request -> switch (request.message().text()) {
                    case "Напомни завтра позвонить маме" -> interpretation(
                            Map.of(
                                    "title", "позвонить маме",
                                    "startDate", "2026-04-17",
                                    "allDay", "false"
                            ),
                            List.of(),
                            List.of("time"),
                            false,
                            0.72d
                    );
                    case "в 10:00" -> interpretation(
                            Map.of(
                                    "startTime", "10:00",
                                    "allDay", "false"
                            ),
                            List.of(),
                            List.of("title", "date"),
                            false,
                            0.90d
                    );
                    default -> throw new IllegalStateException("Unexpected message: " + request.message().text());
                },
                new InMemoryUserContextPort(),
                conversationStatePort,
                new InMemoryIdempotencyPort(),
                calendarPort,
                notificationPort,
                new RecordingAuditPort()
        );

        ExecutionResult first = service.handle(sampleMessage("msg-3", "Напомни завтра позвонить маме"));
        ExecutionResult second = service.handle(sampleMessage("msg-4", "в 10:00"));

        ConversationState state = conversationStatePort.findByConversationId("telegram-chat:101").orElseThrow();
        assertEquals("Во сколько должно начаться событие?", first.userSummary());
        assertTrue(second.success());
        assertEquals(1, calendarPort.createdDrafts.size());
        assertEquals("позвонить маме", calendarPort.createdDrafts.get(0).title());
        assertTrue(second.userSummary().contains("10:00"));
        assertEquals(2, notificationPort.commands.size());
        assertEquals(ConversationStatus.COMPLETED, state.status());
        assertNull(state.pendingAction());
        assertNull(state.clarificationRequest());
        assertNull(state.pendingConfirmation());
    }

    @Test
    void shouldKeepOriginalTitleWhenTimeFollowUpContainsSpuriousTitle() {
        RecordingCalendarPort calendarPort = new RecordingCalendarPort();
        RecordingNotificationPort notificationPort = new RecordingNotificationPort();
        InMemoryConversationStatePort conversationStatePort = new InMemoryConversationStatePort();

        HandleIncomingMessageService service = createService(
                request -> switch (request.message().text()) {
                    case "РєСѓРїРё РјРѕР»РѕРєРѕ" -> interpretation(
                            Map.of(
                                    "title", "РєСѓРїРё РјРѕР»РѕРєРѕ",
                                    "allDay", "false"
                            ),
                            List.of(),
                            List.of("date", "time"),
                            false,
                            0.72d
                    );
                    case "СЃРµРіРѕРґРЅСЏ" -> interpretation(
                            Map.of(
                                    "startDate", "2026-04-17",
                                    "allDay", "false"
                            ),
                            List.of(),
                            List.of("title", "time"),
                            false,
                            0.88d
                    );
                    case "19:00" -> interpretation(
                            Map.of(
                                    "title", "19:00",
                                    "startTime", "19:00",
                                    "allDay", "false"
                            ),
                            List.of(),
                            List.of("date"),
                            false,
                            0.91d
                    );
                    default -> throw new IllegalStateException("Unexpected message: " + request.message().text());
                },
                new InMemoryUserContextPort(),
                conversationStatePort,
                new InMemoryIdempotencyPort(),
                calendarPort,
                notificationPort,
                new RecordingAuditPort()
        );

        ExecutionResult first = service.handle(sampleMessage("msg-3a", "РєСѓРїРё РјРѕР»РѕРєРѕ"));
        ExecutionResult second = service.handle(sampleMessage("msg-3b", "СЃРµРіРѕРґРЅСЏ"));
        ExecutionResult third = service.handle(sampleMessage("msg-3c", "19:00"));

        ConversationState state = conversationStatePort.findByConversationId("telegram-chat:101").orElseThrow();
        assertTrue(first.success());
        assertTrue(second.success());
        assertTrue(third.success());
        assertEquals(1, calendarPort.createdDrafts.size());
        assertEquals("РєСѓРїРё РјРѕР»РѕРєРѕ", calendarPort.createdDrafts.get(0).title());
        assertTrue(third.userSummary().contains("РєСѓРїРё РјРѕР»РѕРєРѕ"));
        assertEquals(ConversationStatus.COMPLETED, state.status());
    }

    @Test
    void shouldAskAgainWhenClarificationFollowUpIsStillAmbiguous() {
        RecordingCalendarPort calendarPort = new RecordingCalendarPort();
        RecordingNotificationPort notificationPort = new RecordingNotificationPort();
        InMemoryConversationStatePort conversationStatePort = new InMemoryConversationStatePort();

        HandleIncomingMessageService service = createService(
                request -> switch (request.message().text()) {
                    case "Напомни завтра позвонить маме" -> interpretation(
                            Map.of(
                                    "title", "позвонить маме",
                                    "startDate", "2026-04-17",
                                    "allDay", "false"
                            ),
                            List.of(),
                            List.of("time"),
                            false,
                            0.72d
                    );
                    case "вечером" -> interpretation(
                            Map.of(),
                            List.of("time_is_range"),
                            List.of("title", "date"),
                            false,
                            0.68d
                    );
                    default -> throw new IllegalStateException("Unexpected message: " + request.message().text());
                },
                new InMemoryUserContextPort(),
                conversationStatePort,
                new InMemoryIdempotencyPort(),
                calendarPort,
                notificationPort,
                new RecordingAuditPort()
        );

        service.handle(sampleMessage("msg-5", "Напомни завтра позвонить маме"));
        ExecutionResult second = service.handle(sampleMessage("msg-6", "вечером"));

        ConversationState state = conversationStatePort.findByConversationId("telegram-chat:101").orElseThrow();
        assertTrue(second.success());
        assertEquals("Во сколько должно начаться событие?", second.userSummary());
        assertTrue(calendarPort.createdDrafts.isEmpty());
        assertEquals(ConversationStatus.AWAITING_TIME, state.status());
        assertNotNull(state.pendingAction());
        assertEquals(2, notificationPort.commands.size());
    }

    @Test
    void shouldRequestConfirmationAndExecuteOnPositiveReply() {
        RecordingCalendarPort calendarPort = new RecordingCalendarPort();
        RecordingNotificationPort notificationPort = new RecordingNotificationPort();
        InMemoryConversationStatePort conversationStatePort = new InMemoryConversationStatePort();
        InMemoryUserContextPort userContextPort = new InMemoryUserContextPort();
        userContextPort.save(requireConfirmationContext());

        HandleIncomingMessageService service = createService(
                request -> completeInterpretation("демо", "2026-04-17", "09:00"),
                userContextPort,
                conversationStatePort,
                new InMemoryIdempotencyPort(),
                calendarPort,
                notificationPort,
                new RecordingAuditPort()
        );

        ExecutionResult first = service.handle(sampleMessage("msg-7", "Создай демо завтра в 09:00"));
        ExecutionResult second = service.handle(sampleMessage("msg-8", "да"));

        ConversationState state = conversationStatePort.findByConversationId("telegram-chat:101").orElseThrow();
        assertTrue(first.success());
        assertTrue(first.userSummary().contains("Ответьте"));
        assertTrue(second.success());
        assertEquals(1, calendarPort.createdDrafts.size());
        assertEquals(ConversationStatus.COMPLETED, state.status());
        assertNull(state.pendingAction());
        assertNull(state.pendingConfirmation());
        assertEquals(2, notificationPort.commands.size());
    }

    @Test
    void shouldRejectPendingConfirmationOnNoReplyAndClearState() {
        RecordingCalendarPort calendarPort = new RecordingCalendarPort();
        RecordingNotificationPort notificationPort = new RecordingNotificationPort();
        InMemoryConversationStatePort conversationStatePort = new InMemoryConversationStatePort();
        InMemoryUserContextPort userContextPort = new InMemoryUserContextPort();
        userContextPort.save(requireConfirmationContext());

        HandleIncomingMessageService service = createService(
                request -> completeInterpretation("демо", "2026-04-17", "09:00"),
                userContextPort,
                conversationStatePort,
                new InMemoryIdempotencyPort(),
                calendarPort,
                notificationPort,
                new RecordingAuditPort()
        );

        service.handle(sampleMessage("msg-9", "Создай демо завтра в 09:00"));
        ExecutionResult second = service.handle(sampleMessage("msg-10", "нет"));

        ConversationState state = conversationStatePort.findByConversationId("telegram-chat:101").orElseThrow();
        assertTrue(second.success());
        assertEquals("Хорошо, не выполняю это действие.", second.userSummary());
        assertTrue(calendarPort.createdDrafts.isEmpty());
        assertEquals(ConversationStatus.CANCELLED, state.status());
        assertNull(state.pendingAction());
        assertNull(state.pendingConfirmation());
    }

    @Test
    void shouldCancelPendingClarificationOnCancelReplyAndClearState() {
        RecordingCalendarPort calendarPort = new RecordingCalendarPort();
        RecordingNotificationPort notificationPort = new RecordingNotificationPort();
        InMemoryConversationStatePort conversationStatePort = new InMemoryConversationStatePort();

        HandleIncomingMessageService service = createService(
                request -> interpretation(
                        Map.of(
                                "title", "позвонить маме",
                                "startDate", "2026-04-17",
                                "allDay", "false"
                        ),
                        List.of(),
                        List.of("time"),
                        false,
                        0.72d
                ),
                new InMemoryUserContextPort(),
                conversationStatePort,
                new InMemoryIdempotencyPort(),
                calendarPort,
                notificationPort,
                new RecordingAuditPort()
        );

        service.handle(sampleMessage("msg-11", "Напомни завтра позвонить маме"));
        ExecutionResult second = service.handle(sampleMessage("msg-12", "отмена"));

        ConversationState state = conversationStatePort.findByConversationId("telegram-chat:101").orElseThrow();
        assertTrue(second.success());
        assertEquals("Хорошо, отменяю текущее действие.", second.userSummary());
        assertTrue(calendarPort.createdDrafts.isEmpty());
        assertEquals(ConversationStatus.CANCELLED, state.status());
        assertNull(state.pendingAction());
        assertNull(state.clarificationRequest());
    }

    @Test
    void shouldFillMultipleMissingFieldsFromSingleFollowUp() {
        RecordingCalendarPort calendarPort = new RecordingCalendarPort();
        RecordingNotificationPort notificationPort = new RecordingNotificationPort();
        InMemoryConversationStatePort conversationStatePort = new InMemoryConversationStatePort();

        HandleIncomingMessageService service = createService(
                request -> switch (request.message().text()) {
                    case "Создай встречу с командой" -> interpretation(
                            Map.of("title", "встреча с командой"),
                            List.of(),
                            List.of("date", "time"),
                            false,
                            0.70d
                    );
                    case "завтра в 10" -> interpretation(
                            Map.of(
                                    "startDate", "2026-04-17",
                                    "startTime", "10:00",
                                    "allDay", "false"
                            ),
                            List.of(),
                            List.of("title"),
                            false,
                            0.92d
                    );
                    default -> throw new IllegalStateException("Unexpected message: " + request.message().text());
                },
                new InMemoryUserContextPort(),
                conversationStatePort,
                new InMemoryIdempotencyPort(),
                calendarPort,
                notificationPort,
                new RecordingAuditPort()
        );

        service.handle(sampleMessage("msg-13", "Создай встречу с командой"));
        ExecutionResult second = service.handle(sampleMessage("msg-14", "завтра в 10"));

        ConversationState state = conversationStatePort.findByConversationId("telegram-chat:101").orElseThrow();
        assertTrue(second.success());
        assertEquals(1, calendarPort.createdDrafts.size());
        assertEquals("встреча с командой", calendarPort.createdDrafts.get(0).title());
        assertTrue(second.userSummary().contains("10:00"));
        assertEquals(ConversationStatus.COMPLETED, state.status());
        assertNull(state.pendingAction());
    }

    @Test
    void shouldRejectUnrelatedNewRequestDuringPendingClarification() {
        RecordingCalendarPort calendarPort = new RecordingCalendarPort();
        RecordingNotificationPort notificationPort = new RecordingNotificationPort();
        InMemoryConversationStatePort conversationStatePort = new InMemoryConversationStatePort();

        HandleIncomingMessageService service = createService(
                request -> switch (request.message().text()) {
                    case "Напомни завтра позвонить маме" -> interpretation(
                            Map.of(
                                    "title", "позвонить маме",
                                    "startDate", "2026-04-17",
                                    "allDay", "false"
                            ),
                            List.of(),
                            List.of("time"),
                            false,
                            0.72d
                    );
                    case "Создай встречу с Петей завтра" -> interpretation(
                            Map.of(
                                    "title", "с петей",
                                    "startDate", "2026-04-17",
                                    "allDay", "false"
                            ),
                            List.of(),
                            List.of("time"),
                            false,
                            0.80d
                    );
                    default -> throw new IllegalStateException("Unexpected message: " + request.message().text());
                },
                new InMemoryUserContextPort(),
                conversationStatePort,
                new InMemoryIdempotencyPort(),
                calendarPort,
                notificationPort,
                new RecordingAuditPort()
        );

        service.handle(sampleMessage("msg-15", "Напомни завтра позвонить маме"));
        ExecutionResult second = service.handle(sampleMessage("msg-16", "Создай встречу с Петей завтра"));

        ConversationState state = conversationStatePort.findByConversationId("telegram-chat:101").orElseThrow();
        assertTrue(second.success());
        assertEquals("Сначала завершите текущий запрос или напишите \"отмена\", а потом начните новый.", second.userSummary());
        assertTrue(calendarPort.createdDrafts.isEmpty());
        assertEquals(ConversationStatus.AWAITING_TIME, state.status());
        assertNotNull(state.pendingAction());
    }

    @Test
    void shouldSkipDuplicateMessageByIdempotencyKey() {
        RecordingCalendarPort calendarPort = new RecordingCalendarPort();
        RecordingNotificationPort notificationPort = new RecordingNotificationPort();

        HandleIncomingMessageService service = createService(
                request -> completeInterpretation("позвонить маме", "2026-04-17", "10:00"),
                new InMemoryUserContextPort(),
                new InMemoryConversationStatePort(),
                new InMemoryIdempotencyPort(),
                calendarPort,
                notificationPort,
                new RecordingAuditPort()
        );

        IncomingUserMessage message = sampleMessage("msg-13", "Напомни завтра в 10:00 позвонить маме");

        ExecutionResult first = service.handle(message);
        ExecutionResult second = service.handle(message);

        assertTrue(first.success());
        assertTrue(second.success());
        assertEquals("duplicate_message_skipped", second.auditDetails());
        assertEquals(1, calendarPort.createdDrafts.size());
        assertEquals(1, notificationPort.commands.size());
    }

    @Test
    void shouldTreatSameExternalMessageIdFromDifferentChatsAsDifferentMessages() {
        RecordingCalendarPort calendarPort = new RecordingCalendarPort();
        RecordingNotificationPort notificationPort = new RecordingNotificationPort();

        HandleIncomingMessageService service = createService(
                request -> completeInterpretation("позвонить маме", "2026-04-17", "10:00"),
                new InMemoryUserContextPort(),
                new InMemoryConversationStatePort(),
                new InMemoryIdempotencyPort(),
                calendarPort,
                notificationPort,
                new RecordingAuditPort()
        );

        IncomingUserMessage firstChatMessage = sampleMessage("msg-17", "Напомни завтра в 10:00 позвонить маме");
        IncomingUserMessage secondChatMessage = new IncomingUserMessage(
                "internal-msg-18",
                "msg-17",
                new UserIdentity("telegram-user:77"),
                ChannelType.TELEGRAM,
                "telegram-chat:202",
                "Напомни завтра в 10:00 позвонить маме",
                Instant.parse("2026-04-16T20:16:30Z")
        );

        service.handle(firstChatMessage);
        ExecutionResult second = service.handle(secondChatMessage);

        assertTrue(second.success());
        assertEquals(2, calendarPort.createdDrafts.size());
        assertEquals(2, notificationPort.commands.size());
    }

    @Test
    void shouldRequireConfirmationWhenExecutionGuardFlagsFarFutureEvent() {
        RecordingCalendarPort calendarPort = new RecordingCalendarPort();
        RecordingNotificationPort notificationPort = new RecordingNotificationPort();
        InMemoryConversationStatePort conversationStatePort = new InMemoryConversationStatePort();

        HandleIncomingMessageService service = createService(
                request -> completeInterpretation("проверка", "2027-11-20", "09:00"),
                new InMemoryUserContextPort(),
                conversationStatePort,
                new InMemoryIdempotencyPort(),
                calendarPort,
                notificationPort,
                new RecordingAuditPort(),
                new CalendarExecutionGuardSettings(120, 500, Duration.ofHours(24), 30),
                Clock.fixed(Instant.parse("2026-04-16T20:15:30Z"), java.time.ZoneOffset.UTC),
                HouseholdNotificationSettings.disabled()
        );

        ExecutionResult first = service.handle(sampleMessage("msg-future-1", "create future check"));
        ConversationState pendingState = conversationStatePort.findByConversationId("telegram-chat:101").orElseThrow();
        assertTrue(first.success());
        assertTrue(first.userSummary().contains("Ответьте"));
        assertTrue(calendarPort.createdDrafts.isEmpty());
        assertEquals(ConversationStatus.AWAITING_CONFIRMATION, pendingState.status());

        ExecutionResult second = service.handle(sampleMessage("msg-future-2", "да"));
        ConversationState state = conversationStatePort.findByConversationId("telegram-chat:101").orElseThrow();
        assertTrue(second.success());
        assertEquals(1, calendarPort.createdDrafts.size());
        assertEquals(ConversationStatus.COMPLETED, state.status());
    }

    @Test
    void shouldAskAgainOnFirstInvalidTimeDuringResumedClarification() {
        RecordingCalendarPort calendarPort = new RecordingCalendarPort();
        RecordingNotificationPort notificationPort = new RecordingNotificationPort();
        InMemoryConversationStatePort conversationStatePort = new InMemoryConversationStatePort();

        HandleIncomingMessageService service = createService(
                request -> switch (request.message().text()) {
                    case "Напомни завтра позвонить маме" -> interpretation(
                            Map.of(
                                    "title", "позвонить маме",
                                    "startDate", "2026-04-17",
                                    "allDay", "false"
                            ),
                            List.of(),
                            List.of("time"),
                            false,
                            0.72d
                    );
                    case "в 25:99" -> interpretation(
                            Map.of(
                                    "startTime", "25:99",
                                    "allDay", "false"
                            ),
                            List.of(),
                            List.of(),
                            true,
                            0.95d
                    );
                    default -> throw new IllegalStateException("Unexpected message: " + request.message().text());
                },
                new InMemoryUserContextPort(),
                conversationStatePort,
                new InMemoryIdempotencyPort(),
                calendarPort,
                notificationPort,
                new RecordingAuditPort()
        );

        service.handle(sampleMessage("msg-guard-clarify-1", "Напомни завтра позвонить маме"));
        ExecutionResult second = service.handle(sampleMessage("msg-guard-clarify-2", "в 25:99"));

        ConversationState state = conversationStatePort.findByConversationId("telegram-chat:101").orElseThrow();
        assertTrue(second.success());
        assertTrue(second.userSummary().contains("некорректно"));
        assertTrue(calendarPort.createdDrafts.isEmpty());
        assertEquals(ConversationStatus.AWAITING_TIME, state.status());
        assertEquals(1, state.invalidClarificationAttempts());
    }

    @Test
    void shouldFailAfterInvalidClarificationAttemptLimitIsReached() {
        RecordingCalendarPort calendarPort = new RecordingCalendarPort();
        RecordingNotificationPort notificationPort = new RecordingNotificationPort();
        InMemoryConversationStatePort conversationStatePort = new InMemoryConversationStatePort();

        HandleIncomingMessageService service = createService(
                request -> switch (request.message().text()) {
                    case "Напомни завтра позвонить маме" -> interpretation(
                            Map.of(
                                    "title", "позвонить маме",
                                    "startDate", "2026-04-17",
                                    "allDay", "false"
                            ),
                            List.of(),
                            List.of("time"),
                            false,
                            0.72d
                    );
                    case "в 25:99", "в 26:99" -> interpretation(
                            Map.of(
                                    "startTime", request.message().text().substring(2),
                                    "allDay", "false"
                            ),
                            List.of(),
                            List.of(),
                            true,
                            0.95d
                    );
                    default -> throw new IllegalStateException("Unexpected message: " + request.message().text());
                },
                new InMemoryUserContextPort(),
                conversationStatePort,
                new InMemoryIdempotencyPort(),
                calendarPort,
                notificationPort,
                new RecordingAuditPort()
        );

        service.handle(sampleMessage("msg-guard-limit-1", "Напомни завтра позвонить маме"));
        service.handle(sampleMessage("msg-guard-limit-2", "в 25:99"));
        ExecutionResult third = service.handle(sampleMessage("msg-guard-limit-3", "в 26:99"));

        ConversationState state = conversationStatePort.findByConversationId("telegram-chat:101").orElseThrow();
        assertFalse(third.success());
        assertTrue(third.userSummary().contains("несколько раз"));
        assertTrue(calendarPort.createdDrafts.isEmpty());
        assertEquals(ConversationStatus.FAILED, state.status());
    }

    @Test
    void shouldTreatRawInvalidTimeAsInvalidClarificationAttemptWhenInterpreterMissesIt() {
        RecordingCalendarPort calendarPort = new RecordingCalendarPort();
        RecordingNotificationPort notificationPort = new RecordingNotificationPort();
        InMemoryConversationStatePort conversationStatePort = new InMemoryConversationStatePort();

        HandleIncomingMessageService service = createService(
                request -> switch (request.message().text()) {
                    case "Напомни завтра позвонить маме" -> interpretation(
                            Map.of(
                                    "title", "позвонить маме",
                                    "startDate", "2026-04-17",
                                    "allDay", "false"
                            ),
                            List.of(),
                            List.of("time"),
                            false,
                            0.72d
                    );
                    case "25:99" -> interpretation(
                            Map.of(),
                            List.of(),
                            List.of("title", "date"),
                            false,
                            0.60d
                    );
                    default -> throw new IllegalStateException("Unexpected message: " + request.message().text());
                },
                new InMemoryUserContextPort(),
                conversationStatePort,
                new InMemoryIdempotencyPort(),
                calendarPort,
                notificationPort,
                new RecordingAuditPort()
        );

        service.handle(sampleMessage("msg-raw-invalid-1", "Напомни завтра позвонить маме"));
        ExecutionResult second = service.handle(sampleMessage("msg-raw-invalid-2", "25:99"));

        ConversationState state = conversationStatePort.findByConversationId("telegram-chat:101").orElseThrow();
        assertTrue(second.success());
        assertTrue(second.userSummary().contains("некорректно"));
        assertTrue(calendarPort.createdDrafts.isEmpty());
        assertEquals(ConversationStatus.AWAITING_TIME, state.status());
        assertEquals(1, state.invalidClarificationAttempts());
    }

    @Test
    void shouldSendHouseholdNotificationWhenWifeCreatesEvent() {
        RecordingCalendarPort calendarPort = new RecordingCalendarPort();
        RecordingNotificationPort notificationPort = new RecordingNotificationPort();

        HandleIncomingMessageService service = createService(
                request -> completeInterpretation("стоматолог", "2026-04-17", "14:00"),
                new InMemoryUserContextPort(),
                new InMemoryConversationStatePort(),
                new InMemoryIdempotencyPort(),
                calendarPort,
                notificationPort,
                new RecordingAuditPort(),
                new CalendarExecutionGuardSettings(120, 500, Duration.ofHours(24), 180),
                Clock.fixed(Instant.parse("2026-04-16T20:15:30Z"), java.time.ZoneOffset.UTC),
                wifeToMeHousehold()
        );

        ExecutionResult result = service.handle(sampleMessageFor(
                "telegram-user:84",
                "telegram-chat:202",
                "wife-create-1",
                "Создай стоматолог завтра в 14:00"
        ));

        assertTrue(result.success());
        assertEquals(1, calendarPort.createdDrafts.size());
        assertEquals(2, notificationPort.commands.size());
        assertTrue(notificationPort.commands.stream().anyMatch(command ->
                command.conversationId().equals("telegram-chat:101")
                        && command.text().contains("Жена создала событие")
                        && command.text().contains("стоматолог")
        ));
    }

    @Test
    void shouldSendHouseholdNotificationWhenWifeIsConfiguredByChatOnly() {
        RecordingCalendarPort calendarPort = new RecordingCalendarPort();
        RecordingNotificationPort notificationPort = new RecordingNotificationPort();

        HandleIncomingMessageService service = createService(
                request -> completeInterpretation("СЃС‚РѕРјР°С‚РѕР»РѕРі", "2026-04-17", "14:00"),
                new InMemoryUserContextPort(),
                new InMemoryConversationStatePort(),
                new InMemoryIdempotencyPort(),
                calendarPort,
                notificationPort,
                new RecordingAuditPort(),
                new CalendarExecutionGuardSettings(120, 500, Duration.ofHours(24), 180),
                Clock.fixed(Instant.parse("2026-04-16T20:15:30Z"), java.time.ZoneOffset.UTC),
                wifeChatOnlyToMeHousehold()
        );

        ExecutionResult result = service.handle(sampleMessageFor(
                "telegram-user:999",
                "telegram-chat:202",
                "wife-chat-only-1",
                "РЎРѕР·РґР°Р№ СЃС‚РѕРјР°С‚РѕР»РѕРі Р·Р°РІС‚СЂР° РІ 14:00"
        ));

        assertTrue(result.success());
        assertEquals(1, calendarPort.createdDrafts.size());
        assertEquals(2, notificationPort.commands.size());
        assertTrue(notificationPort.commands.stream().anyMatch(command ->
                command.conversationId().equals("telegram-chat:101")
                        && command.text().contains("Р–РµРЅР°")
        ));
    }

    @Test
    void shouldNotSendHouseholdNotificationWhenCalendarExecutionFails() {
        RecordingNotificationPort notificationPort = new RecordingNotificationPort();

        HandleIncomingMessageService service = createService(
                request -> completeInterpretation("стоматолог", "2026-04-17", "14:00"),
                new InMemoryUserContextPort(),
                new InMemoryConversationStatePort(),
                new InMemoryIdempotencyPort(),
                new FailingCalendarPort(),
                notificationPort,
                new RecordingAuditPort(),
                new CalendarExecutionGuardSettings(120, 500, Duration.ofHours(24), 180),
                Clock.fixed(Instant.parse("2026-04-16T20:15:30Z"), java.time.ZoneOffset.UTC),
                wifeToMeHousehold()
        );

        ExecutionResult result = service.handle(sampleMessageFor(
                "telegram-user:84",
                "telegram-chat:202",
                "wife-create-failed",
                "Создай стоматолог завтра в 14:00"
        ));

        assertFalse(result.success());
        assertEquals(1, notificationPort.commands.size());
        assertTrue(notificationPort.commands.stream().noneMatch(command -> command.conversationId().equals("telegram-chat:101")));
    }

    @Test
    void shouldNotDuplicateHouseholdNotificationWhenDuplicateMessageIsSkipped() {
        RecordingCalendarPort calendarPort = new RecordingCalendarPort();
        RecordingNotificationPort notificationPort = new RecordingNotificationPort();

        HandleIncomingMessageService service = createService(
                request -> completeInterpretation("стоматолог", "2026-04-17", "14:00"),
                new InMemoryUserContextPort(),
                new InMemoryConversationStatePort(),
                new InMemoryIdempotencyPort(),
                calendarPort,
                notificationPort,
                new RecordingAuditPort(),
                new CalendarExecutionGuardSettings(120, 500, Duration.ofHours(24), 180),
                Clock.fixed(Instant.parse("2026-04-16T20:15:30Z"), java.time.ZoneOffset.UTC),
                wifeToMeHousehold()
        );

        IncomingUserMessage message = sampleMessageFor(
                "telegram-user:84",
                "telegram-chat:202",
                "wife-duplicate-1",
                "Создай стоматолог завтра в 14:00"
        );

        service.handle(message);
        ExecutionResult duplicateResult = service.handle(message);

        assertTrue(duplicateResult.success());
        assertEquals("duplicate_message_skipped", duplicateResult.auditDetails());
        assertEquals(1, calendarPort.createdDrafts.size());
        assertEquals(1, notificationPort.commands.stream()
                .filter(command -> command.conversationId().equals("telegram-chat:101"))
                .count());
    }

    private HandleIncomingMessageService createService(
            InterpretationScript interpretationScript,
            UserContextPort userContextPort,
            ConversationStatePort conversationStatePort,
            IdempotencyPort idempotencyPort,
            CalendarPort calendarPort,
            NotificationPort notificationPort,
            AuditPort auditPort
    ) {
        return createService(
                interpretationScript,
                userContextPort,
                conversationStatePort,
                idempotencyPort,
                calendarPort,
                notificationPort,
                auditPort,
                new CalendarExecutionGuardSettings(120, 500, Duration.ofHours(24), 180),
                Clock.fixed(Instant.parse("2026-04-16T20:15:30Z"), java.time.ZoneOffset.UTC),
                HouseholdNotificationSettings.disabled()
        );
    }

    private HandleIncomingMessageService createService(
            InterpretationScript interpretationScript,
            UserContextPort userContextPort,
            ConversationStatePort conversationStatePort,
            IdempotencyPort idempotencyPort,
            CalendarPort calendarPort,
            NotificationPort notificationPort,
            AuditPort auditPort,
            CalendarExecutionGuardSettings guardSettings,
            Clock clock,
            HouseholdNotificationSettings householdSettings
    ) {
        return new HandleIncomingMessageService(
                userContextPort,
                conversationStatePort,
                idempotencyPort,
                new ScriptedIntentInterpreterPort(interpretationScript),
                notificationPort,
                auditPort,
                new IntentRoutingService(new ConfirmationPolicyService()),
                new CreateCalendarEventHandler(calendarPort, new HouseholdNotificationService(householdSettings)),
                new CalendarEventDraftFactory(),
                new CalendarExecutionGuard(guardSettings, clock),
                new ClarificationRetryService(new ClarificationRetrySettings(2)),
                new ClarificationHandler(),
                new PendingConfirmationHandler(),
                new PendingActionFactory(),
                new PendingActionMergeService(),
                new ConversationControlService(),
                new PendingFlowInterruptionService(),
                new DefaultUserContextFactory(
                        new Timezone("Europe/Moscow"),
                        "ru",
                        ConfirmationPreference.AUTO_EXECUTE,
                        Duration.ofHours(1)
                )
        );
    }

    private IntentInterpretation completeInterpretation(String title, String startDate, String startTime) {
        return interpretation(
                Map.of(
                        "title", title,
                        "startDate", startDate,
                        "startTime", startTime,
                        "allDay", "false"
                ),
                List.of(),
                List.of(),
                true,
                0.95d
        );
    }

    private IntentInterpretation interpretation(
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
        return sampleMessageFor("telegram-user:42", "telegram-chat:101", externalMessageId, text);
    }

    private IncomingUserMessage sampleMessageFor(
            String userId,
            String conversationId,
            String externalMessageId,
            String text
    ) {
        return new IncomingUserMessage(
                "internal-" + externalMessageId,
                externalMessageId,
                new UserIdentity(userId),
                ChannelType.TELEGRAM,
                conversationId,
                text,
                Instant.parse("2026-04-16T20:15:30Z")
        );
    }

    private UserContext requireConfirmationContext() {
        return new UserContext(
                new UserIdentity("telegram-user:42"),
                new Timezone("Europe/Moscow"),
                "ru",
                ConfirmationPreference.REQUIRE_CONFIRMATION,
                Duration.ofHours(1),
                "telegram-chat:101"
        );
    }

    private HouseholdNotificationSettings wifeToMeHousehold() {
        return new HouseholdNotificationSettings(
                true,
                Map.of(
                        "me", new HouseholdMember(
                                "me",
                                "Я",
                                new UserIdentity("telegram-user:42"),
                                List.of("telegram-chat:101"),
                                List.of()
                        ),
                        "wife", new HouseholdMember(
                                "wife",
                                "Жена",
                                new UserIdentity("telegram-user:84"),
                                List.of("telegram-chat:202"),
                                List.of("me")
                        )
                )
        );
    }

    private HouseholdNotificationSettings wifeChatOnlyToMeHousehold() {
        return new HouseholdNotificationSettings(
                true,
                Map.of(
                        "me", new HouseholdMember(
                                "me",
                                "РЇ",
                                new UserIdentity("telegram-user:42"),
                                List.of("telegram-chat:101"),
                                List.of()
                        ),
                        "wife", new HouseholdMember(
                                "wife",
                                "Р–РµРЅР°",
                                new UserIdentity("household-member:wife"),
                                List.of("telegram-chat:202"),
                                List.of("me")
                        )
                )
        );
    }

    @FunctionalInterface
    private interface InterpretationScript {
        IntentInterpretation apply(IntentInterpretationRequest request);
    }

    private static final class ScriptedIntentInterpreterPort implements IntentInterpreterPort {
        private final InterpretationScript interpretationScript;

        private ScriptedIntentInterpreterPort(InterpretationScript interpretationScript) {
            this.interpretationScript = interpretationScript;
        }

        @Override
        public IntentInterpretation interpret(IntentInterpretationRequest request) {
            return interpretationScript.apply(request);
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

    private static final class FailingCalendarPort implements CalendarPort {
        @Override
        public CalendarEventReference createEvent(CalendarEventDraft draft) {
            throw new IllegalStateException("calendar unavailable");
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
