package io.github.nadya.assistant.domain.calendar;

import io.github.nadya.assistant.domain.common.RecurrenceRule;
import io.github.nadya.assistant.domain.common.Timezone;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record CalendarEventDraft(
        String title,
        String description,
        ZonedDateTime start,
        ZonedDateTime end,
        boolean allDay,
        Timezone timezone,
        RecurrenceRule recurrenceRule,
        String location,
        Map<String, String> sourceMetadata
) {

    public CalendarEventDraft {
        title = Objects.requireNonNullElse(title, "").trim();
        if (title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        start = Objects.requireNonNull(start, "start must not be null");
        end = Objects.requireNonNull(end, "end must not be null");
        timezone = Objects.requireNonNull(timezone, "timezone must not be null");
        sourceMetadata = Map.copyOf(sourceMetadata == null ? Map.of() : new LinkedHashMap<>(sourceMetadata));
    }
}
