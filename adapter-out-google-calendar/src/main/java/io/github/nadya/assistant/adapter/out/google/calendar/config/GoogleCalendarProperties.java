package io.github.nadya.assistant.adapter.out.google.calendar.config;

public record GoogleCalendarProperties(boolean enabled, String calendarId, boolean stubMode) {

    public GoogleCalendarProperties {
        calendarId = calendarId == null || calendarId.isBlank() ? "primary" : calendarId;
    }
}
