package io.github.nadya.assistant.application.handler;

import io.github.nadya.assistant.application.command.MessageHandlingContext;
import io.github.nadya.assistant.application.result.HandlingOutcome;
import io.github.nadya.assistant.domain.calendar.CalendarEventDraft;
import io.github.nadya.assistant.domain.calendar.CalendarEventReference;
import io.github.nadya.assistant.domain.conversation.ConversationState;
import io.github.nadya.assistant.domain.execution.ExecutionResult;
import io.github.nadya.assistant.ports.out.CalendarPort;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;

public final class CreateCalendarEventHandler {

    private final CalendarPort calendarPort;

    public CreateCalendarEventHandler(CalendarPort calendarPort) {
        this.calendarPort = calendarPort;
    }

    public HandlingOutcome handle(MessageHandlingContext context) {
        CalendarEventDraft draft = buildDraft(context);
        CalendarEventReference reference = calendarPort.createEvent(draft);

        String summary = "Событие \"%s\" создано.".formatted(draft.title());
        ExecutionResult executionResult = ExecutionResult.completed(
                reference.externalId(),
                summary,
                "calendar_event_created"
        );

        return new HandlingOutcome(executionResult, context.conversationState().completed());
    }

    private CalendarEventDraft buildDraft(MessageHandlingContext context) {
        String title = context.interpretation().assistantIntent().entities().getOrDefault("title", context.message().text());
        LocalDate startDate = LocalDate.parse(context.interpretation().assistantIntent().entities().get("startDate"));
        LocalTime startTime = LocalTime.parse(context.interpretation().assistantIntent().entities().get("startTime"));
        ZonedDateTime start = ZonedDateTime.of(startDate, startTime, context.userContext().preferredTimezone().toZoneId());
        ZonedDateTime end = start.plus(context.userContext().defaultEventDuration());

        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        metadata.put("sourceMessageId", context.message().externalMessageId());
        metadata.put("channel", context.message().channelType().name());

        return new CalendarEventDraft(
                title,
                "Created from message: " + context.message().text(),
                start,
                end,
                context.userContext().preferredTimezone(),
                null,
                null,
                metadata
        );
    }
}
