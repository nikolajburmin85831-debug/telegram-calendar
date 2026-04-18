package io.github.nadya.assistant.domain.calendar;

import io.github.nadya.assistant.domain.common.RecurrenceRule;
import io.github.nadya.assistant.domain.common.Timezone;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

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
        title = title == null ? "" : title.trim();
        description = description == null ? "" : description.trim();
        location = location == null ? "" : location.trim();
        sourceMetadata = Map.copyOf(sourceMetadata == null ? Map.of() : new LinkedHashMap<>(sourceMetadata));
    }

    public String metadata(String key) {
        return sourceMetadata.getOrDefault(key, "");
    }
}
