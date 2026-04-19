package io.github.nadya.assistant.application.service;

import io.github.nadya.assistant.application.command.MessageHandlingContext;
import io.github.nadya.assistant.domain.calendar.CalendarActionType;
import io.github.nadya.assistant.domain.calendar.CalendarEventDraft;
import io.github.nadya.assistant.domain.common.ChannelType;
import io.github.nadya.assistant.domain.common.ConfidenceScore;
import io.github.nadya.assistant.domain.common.RecurrenceRule;
import io.github.nadya.assistant.domain.common.Timezone;
import io.github.nadya.assistant.domain.conversation.ConversationState;
import io.github.nadya.assistant.domain.conversation.IncomingUserMessage;
import io.github.nadya.assistant.domain.execution.ExecutionApproval;
import io.github.nadya.assistant.domain.execution.ExecutionGuardOutcome;
import io.github.nadya.assistant.domain.intent.AssistantIntent;
import io.github.nadya.assistant.domain.intent.IntentInterpretation;
import io.github.nadya.assistant.domain.intent.IntentType;
import io.github.nadya.assistant.domain.user.ConfirmationPreference;
import io.github.nadya.assistant.domain.user.UserContext;
import io.github.nadya.assistant.domain.user.UserIdentity;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalendarExecutionGuardTest {

    private final CalendarExecutionGuard guard = new CalendarExecutionGuard(
            new CalendarExecutionGuardSettings(120, 500, Duration.ofHours(24), 180),
            Clock.fixed(Instant.parse("2026-04-16T20:15:30Z"), ZoneOffset.UTC)
    );

    @Test
    void validEventAllowed() {
        CalendarEventDraft draft = draft(
                "Dentist",
                at("2026-04-17T14:00:00+03:00"),
                at("2026-04-17T15:00:00+03:00"),
                false,
                null,
                Map.of()
        );

        assertEquals(ExecutionGuardOutcome.ALLOW, guard.evaluate(CalendarActionType.CREATE, draft, sampleContext()).outcome());
    }

    @Test
    void missingTitleRejected() {
        CalendarEventDraft draft = draft(
                "",
                at("2026-04-17T14:00:00+03:00"),
                at("2026-04-17T15:00:00+03:00"),
                false,
                null,
                Map.of()
        );

        assertEquals(ExecutionGuardOutcome.REJECT, guard.evaluate(CalendarActionType.CREATE, draft, sampleContext()).outcome());
    }

    @Test
    void invalidTimeStructureRejected() {
        CalendarEventDraft draft = draft(
                "Dentist",
                null,
                null,
                false,
                null,
                Map.of(
                        "assistant.rawStartDate", "2026-04-17",
                        "assistant.rawStartTime", "25:99",
                        "assistant.parseError.startTime", "true"
                )
        );

        assertEquals(ExecutionGuardOutcome.REJECT, guard.evaluate(CalendarActionType.CREATE, draft, sampleContext()).outcome());
    }

    @Test
    void oversizedDurationRejected() {
        CalendarEventDraft draft = draft(
                "Trip",
                at("2026-04-17T08:00:00+03:00"),
                at("2026-04-18T12:30:00+03:00"),
                false,
                null,
                Map.of()
        );

        assertEquals(ExecutionGuardOutcome.REJECT, guard.evaluate(CalendarActionType.CREATE, draft, sampleContext()).outcome());
    }

    @Test
    void invalidTimeRangeRejected() {
        CalendarEventDraft draft = draft(
                "Trip",
                at("2026-04-17T15:00:00+03:00"),
                at("2026-04-17T14:00:00+03:00"),
                false,
                null,
                Map.of()
        );

        assertEquals(ExecutionGuardOutcome.REJECT, guard.evaluate(CalendarActionType.CREATE, draft, sampleContext()).outcome());
    }

    @Test
    void pastTimedEventRejected() {
        CalendarEventDraft draft = draft(
                "Reminder",
                at("2026-04-16T22:00:00+03:00"),
                at("2026-04-16T23:00:00+03:00"),
                false,
                null,
                Map.of()
        );

        assertEquals(ExecutionGuardOutcome.REJECT, guard.evaluate(CalendarActionType.CREATE, draft, sampleContext()).outcome());
    }

    @Test
    void farFutureEventRequiresConfirmation() {
        CalendarEventDraft draft = draft(
                "Trip",
                at("2027-11-20T09:00:00+03:00"),
                at("2027-11-20T10:00:00+03:00"),
                false,
                null,
                Map.of()
        );

        assertEquals(ExecutionGuardOutcome.REQUIRE_CONFIRMATION, guard.evaluate(CalendarActionType.CREATE, draft, sampleContext()).outcome());
    }

    @Test
    void recurringEventRequiresConfirmation() {
        CalendarEventDraft draft = draft(
                "Gym",
                at("2026-04-17T18:00:00+03:00"),
                at("2026-04-17T19:00:00+03:00"),
                false,
                new RecurrenceRule("RRULE:FREQ=WEEKLY"),
                Map.of()
        );

        assertEquals(ExecutionGuardOutcome.REQUIRE_CONFIRMATION, guard.evaluate(CalendarActionType.CREATE, draft, sampleContext()).outcome());
    }

    private CalendarEventDraft draft(
            String title,
            ZonedDateTime start,
            ZonedDateTime end,
            boolean allDay,
            RecurrenceRule recurrenceRule,
            Map<String, String> metadata
    ) {
        return new CalendarEventDraft(
                title,
                "Created from assistant request",
                start,
                end,
                allDay,
                new Timezone("Europe/Moscow"),
                recurrenceRule,
                "",
                metadata
        );
    }

    private MessageHandlingContext sampleContext() {
        IncomingUserMessage message = new IncomingUserMessage(
                "internal-guard",
                "guard",
                new UserIdentity("telegram-user:42"),
                ChannelType.TELEGRAM,
                "telegram-chat:101",
                "Create event",
                Instant.parse("2026-04-16T20:15:30Z")
        );
        UserContext userContext = new UserContext(
                message.userId(),
                new Timezone("Europe/Moscow"),
                "ru",
                ConfirmationPreference.AUTO_EXECUTE,
                Duration.ofHours(1),
                message.conversationId()
        );
        return new MessageHandlingContext(
                message,
                message,
                userContext,
                ConversationState.idle(message.conversationId(), message.userId()),
                new IntentInterpretation(
                        new AssistantIntent(IntentType.CREATE_CALENDAR_EVENT, Map.of("title", "test")),
                        new ConfidenceScore(0.95d),
                        List.of(),
                        List.of(),
                        true
                ),
                ExecutionApproval.NOT_CONFIRMED
        );
    }

    private ZonedDateTime at(String isoZonedDateTime) {
        return ZonedDateTime.parse(isoZonedDateTime);
    }
}
