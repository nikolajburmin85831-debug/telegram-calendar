package io.github.nadya.assistant.application.result;

import io.github.nadya.assistant.application.query.AgendaQuery;
import io.github.nadya.assistant.domain.calendar.CalendarAgendaEvent;

import java.util.List;
import java.util.Objects;

public record AgendaResult(
        AgendaQuery query,
        List<CalendarAgendaEvent> events
) {

    public AgendaResult {
        Objects.requireNonNull(query, "query must not be null");
        events = List.copyOf(events == null ? List.of() : events);
    }
}
