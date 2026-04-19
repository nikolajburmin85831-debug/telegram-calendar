package io.github.nadya.assistant.application.query;

import io.github.nadya.assistant.domain.calendar.CalendarDateRange;
import io.github.nadya.assistant.domain.common.Timezone;

import java.util.Objects;

public record AgendaQuery(
        AgendaRange range,
        CalendarDateRange dateRange,
        Timezone timezone
) {

    public AgendaQuery {
        Objects.requireNonNull(range, "range must not be null");
        Objects.requireNonNull(dateRange, "dateRange must not be null");
        Objects.requireNonNull(timezone, "timezone must not be null");
    }
}
