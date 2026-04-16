package io.github.nadya.assistant.adapter.out.google.calendar.oauth;

import io.github.nadya.assistant.adapter.out.google.calendar.config.GoogleCalendarProperties;

public final class GoogleCalendarOAuthSupport {

    public boolean isReady(GoogleCalendarProperties properties) {
        // TODO: wire real OAuth credential loading and refresh flow for Google Calendar API access.
        return properties.enabled();
    }
}
