package io.github.nadya.assistant.adapter.out.google.calendar.mapper;

import io.github.nadya.assistant.adapter.out.google.calendar.client.GoogleCalendarClient;
import io.github.nadya.assistant.adapter.out.google.calendar.config.GoogleCalendarProperties;
import io.github.nadya.assistant.domain.calendar.CalendarEventDraft;

import java.time.format.DateTimeFormatter;

public final class GoogleCalendarRequestMapper {

    private static final DateTimeFormatter RFC3339_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    public GoogleCalendarClient.InsertRequest map(CalendarEventDraft draft, GoogleCalendarProperties properties) {
        return new GoogleCalendarClient.InsertRequest(
                properties.calendarId(),
                draft.title(),
                draft.description(),
                RFC3339_DATE_TIME_FORMATTER.format(draft.start().toOffsetDateTime()),
                RFC3339_DATE_TIME_FORMATTER.format(draft.end().toOffsetDateTime()),
                draft.allDay(),
                draft.timezone().value(),
                draft.location()
        );
    }
}
