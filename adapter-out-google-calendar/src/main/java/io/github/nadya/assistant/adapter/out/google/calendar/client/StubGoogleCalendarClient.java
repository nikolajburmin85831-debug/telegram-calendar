package io.github.nadya.assistant.adapter.out.google.calendar.client;

import java.util.UUID;

public final class StubGoogleCalendarClient implements GoogleCalendarClient {

    @Override
    public InsertResponse insert(InsertRequest request) {
        String eventId = "stub-google-event-" + UUID.randomUUID();
        String link = "google-calendar://events/" + eventId;
        return new InsertResponse(eventId, link);
    }
}
