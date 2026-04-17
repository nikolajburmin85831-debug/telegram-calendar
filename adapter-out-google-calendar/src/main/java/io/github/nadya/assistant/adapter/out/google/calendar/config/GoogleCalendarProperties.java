package io.github.nadya.assistant.adapter.out.google.calendar.config;

public record GoogleCalendarProperties(
        boolean enabled,
        String calendarId,
        boolean stubMode,
        String baseUrl
) {

    public GoogleCalendarProperties {
        calendarId = calendarId == null || calendarId.isBlank() ? "primary" : calendarId;
        baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "https://www.googleapis.com/calendar/v3"
                : baseUrl.trim();
    }
}
