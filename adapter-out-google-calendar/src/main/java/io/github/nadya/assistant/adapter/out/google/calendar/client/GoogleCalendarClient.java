package io.github.nadya.assistant.adapter.out.google.calendar.client;

public interface GoogleCalendarClient {

    InsertResponse insert(InsertRequest request);

    record InsertRequest(
            String calendarId,
            String summary,
            String description,
            String startDateTime,
            String endDateTime,
            String timezone,
            String location
    ) {
    }

    record InsertResponse(String eventId, String htmlLink) {
    }
}
