package io.github.nadya.assistant.domain.calendar;

import java.time.ZonedDateTime;
import java.util.Objects;

public record CalendarAgendaEvent(
        String title,
        ZonedDateTime start,
        ZonedDateTime end,
        boolean allDay,
        String location
) {

    public CalendarAgendaEvent {
        title = title == null ? "" : title.trim();
        location = location == null ? "" : location.trim();
        Objects.requireNonNull(start, "start must not be null");
        Objects.requireNonNull(end, "end must not be null");
        if (title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end must be after start");
        }
    }
}
