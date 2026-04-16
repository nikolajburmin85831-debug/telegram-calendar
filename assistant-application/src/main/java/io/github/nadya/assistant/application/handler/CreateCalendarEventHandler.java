package io.github.nadya.assistant.application.handler;

import io.github.nadya.assistant.application.command.MessageHandlingContext;
import io.github.nadya.assistant.application.result.HandlingOutcome;
import io.github.nadya.assistant.domain.calendar.CalendarEventDraft;
import io.github.nadya.assistant.domain.calendar.CalendarEventReference;
import io.github.nadya.assistant.domain.execution.ExecutionResult;
import io.github.nadya.assistant.ports.out.CalendarPort;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CreateCalendarEventHandler {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final CalendarPort calendarPort;

    public CreateCalendarEventHandler(CalendarPort calendarPort) {
        this.calendarPort = calendarPort;
    }

    public HandlingOutcome handle(MessageHandlingContext context) {
        CalendarEventDraft draft = buildDraft(context);
        CalendarEventReference reference = calendarPort.createEvent(draft);

        ExecutionResult executionResult = ExecutionResult.completed(
                reference.externalId(),
                buildUserSummary(draft),
                "calendar_event_created"
        );

        return new HandlingOutcome(executionResult, context.conversationState().completed());
    }

    private CalendarEventDraft buildDraft(MessageHandlingContext context) {
        Map<String, String> entities = context.interpretation().assistantIntent().entities();
        String title = entities.getOrDefault("title", context.sourceMessage().text());
        LocalDate startDate = LocalDate.parse(entities.get("startDate"));
        boolean allDay = Boolean.parseBoolean(entities.getOrDefault("allDay", "false"));

        ZonedDateTime start;
        ZonedDateTime end;
        if (allDay) {
            start = startDate.atStartOfDay(context.userContext().preferredTimezone().toZoneId());
            end = start.plusDays(1);
        } else {
            LocalTime startTime = LocalTime.parse(entities.get("startTime"));
            start = ZonedDateTime.of(startDate, startTime, context.userContext().preferredTimezone().toZoneId());
            end = start.plus(context.userContext().defaultEventDuration());
        }

        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        metadata.put("sourceMessageId", context.sourceMessage().externalMessageId());
        metadata.put("internalMessageId", context.sourceMessage().internalMessageId());
        metadata.put("conversationId", context.sourceMessage().conversationId());
        metadata.put("channel", context.sourceMessage().channelType().name());

        return new CalendarEventDraft(
                title,
                "Created from assistant request: " + context.sourceMessage().text(),
                start,
                end,
                allDay,
                context.userContext().preferredTimezone(),
                null,
                entities.get("location"),
                metadata
        );
    }

    private String buildUserSummary(CalendarEventDraft draft) {
        String datePart = DATE_FORMATTER.format(draft.start());
        String timePart = draft.allDay() ? "на весь день" : "в " + TIME_FORMATTER.format(draft.start());
        return "Создала событие \"%s\" на %s %s.".formatted(draft.title(), datePart, timePart);
    }
}
