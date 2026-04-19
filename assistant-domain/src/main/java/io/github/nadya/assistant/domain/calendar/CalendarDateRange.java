package io.github.nadya.assistant.domain.calendar;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

public record CalendarDateRange(
        ZonedDateTime startInclusive,
        ZonedDateTime endExclusive
) {

    public CalendarDateRange {
        Objects.requireNonNull(startInclusive, "startInclusive must not be null");
        Objects.requireNonNull(endExclusive, "endExclusive must not be null");
        if (!endExclusive.isAfter(startInclusive)) {
            throw new IllegalArgumentException("endExclusive must be after startInclusive");
        }
    }

    public static CalendarDateRange forDay(LocalDate day, ZoneId zoneId) {
        Objects.requireNonNull(day, "day must not be null");
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        ZonedDateTime start = day.atStartOfDay(zoneId);
        return new CalendarDateRange(start, start.plusDays(1));
    }
}
