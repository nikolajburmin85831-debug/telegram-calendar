package io.github.nadya.assistant.application.handler;

import io.github.nadya.assistant.application.command.MessageHandlingContext;
import io.github.nadya.assistant.application.result.HandlingOutcome;
import io.github.nadya.assistant.application.service.HouseholdNotificationService;
import io.github.nadya.assistant.domain.calendar.CalendarActionType;
import io.github.nadya.assistant.domain.calendar.CalendarEventDraft;
import io.github.nadya.assistant.domain.calendar.CalendarEventReference;
import io.github.nadya.assistant.domain.execution.ExecutionResult;
import io.github.nadya.assistant.ports.out.CalendarPort;
import java.time.format.DateTimeFormatter;

public final class CreateCalendarEventHandler {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final CalendarPort calendarPort;
    private final HouseholdNotificationService householdNotificationService;

    public CreateCalendarEventHandler(
            CalendarPort calendarPort,
            HouseholdNotificationService householdNotificationService
    ) {
        this.calendarPort = calendarPort;
        this.householdNotificationService = householdNotificationService;
    }

    public HandlingOutcome handle(MessageHandlingContext context, CalendarEventDraft draft) {
        CalendarEventReference reference = calendarPort.createEvent(draft);

        ExecutionResult executionResult = ExecutionResult.completed(
                reference.externalId(),
                buildUserSummary(draft),
                "calendar_event_created"
        );

        return new HandlingOutcome(
                executionResult,
                context.conversationState().completed(),
                householdNotificationService.buildNotifications(context, CalendarActionType.CREATE, draft)
        );
    }

    private String buildUserSummary(CalendarEventDraft draft) {
        String datePart = DATE_FORMATTER.format(draft.start());
        String timePart = draft.allDay() ? "на весь день" : "в " + TIME_FORMATTER.format(draft.start());
        return "Создала событие \"%s\" на %s %s.".formatted(draft.title(), datePart, timePart);
    }
}
