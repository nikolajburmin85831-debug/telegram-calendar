package io.github.nadya.assistant.application.service;

import io.github.nadya.assistant.application.command.MessageHandlingContext;
import io.github.nadya.assistant.application.query.AgendaQuery;
import io.github.nadya.assistant.application.query.AgendaRange;
import io.github.nadya.assistant.domain.calendar.CalendarDateRange;

import java.time.LocalDate;

public final class AgendaQueryFactory {

    public AgendaQuery build(MessageHandlingContext context) {
        AgendaRange range = AgendaRange.fromEntityValue(
                context.interpretation().assistantIntent().entities().get("agendaRange")
        ).orElseThrow(() -> new IllegalArgumentException("Agenda range is missing or unsupported"));

        LocalDate baseDate = context.sourceMessage()
                .receivedAt()
                .atZone(context.userContext().preferredTimezone().toZoneId())
                .toLocalDate();

        LocalDate targetDate = switch (range) {
            case TODAY -> baseDate;
            case TOMORROW -> baseDate.plusDays(1);
        };

        return new AgendaQuery(
                range,
                CalendarDateRange.forDay(targetDate, context.userContext().preferredTimezone().toZoneId()),
                context.userContext().preferredTimezone()
        );
    }
}
