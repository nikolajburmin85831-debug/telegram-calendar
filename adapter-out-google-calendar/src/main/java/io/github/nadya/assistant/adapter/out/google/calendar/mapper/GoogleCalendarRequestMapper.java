package io.github.nadya.assistant.adapter.out.google.calendar.mapper;

import io.github.nadya.assistant.adapter.out.google.calendar.client.GoogleCalendarClient;
import io.github.nadya.assistant.adapter.out.google.calendar.config.GoogleCalendarProperties;
import io.github.nadya.assistant.domain.calendar.CalendarEventDraft;

public final class GoogleCalendarRequestMapper {

    public GoogleCalendarClient.InsertRequest map(CalendarEventDraft draft, GoogleCalendarProperties properties) {
        return new GoogleCalendarClient.InsertRequest(
                properties.calendarId(),
                draft.title(),
                draft.description(),
                draft.start().toOffsetDateTime().toString(),
                draft.end().toOffsetDateTime().toString(),
                draft.timezone().value(),
                draft.location()
        );
    }
}
