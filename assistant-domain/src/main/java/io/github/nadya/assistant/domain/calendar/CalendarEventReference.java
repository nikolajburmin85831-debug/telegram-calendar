package io.github.nadya.assistant.domain.calendar;

import java.util.Objects;

public record CalendarEventReference(String externalId, String humanReadableReference) {

    public CalendarEventReference {
        Objects.requireNonNull(externalId, "externalId must not be null");
    }
}
