package io.github.nadya.assistant.adapter.out.google.calendar.client;

import java.util.List;

public interface GoogleCalendarClient {

    InsertResponse insert(InsertRequest request);

    List<ListItem> list(ListRequest request);

    record InsertRequest(
            String calendarId,
            String summary,
            String description,
            String startDateTime,
            String endDateTime,
            boolean allDay,
            String timezone,
            String location
    ) {
    }

    record InsertResponse(String eventId, String htmlLink) {
    }

    record ListRequest(
            String calendarId,
            String timeMin,
            String timeMax,
            String timeZone
    ) {
    }

    record ListItem(
            String summary,
            String startDateTime,
            String startDate,
            String endDateTime,
            String endDate,
            String timeZone,
            String location
    ) {
    }
}
