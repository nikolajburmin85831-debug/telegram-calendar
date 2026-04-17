package io.github.nadya.assistant.app.ec2.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "assistant.google-calendar")
public record AssistantGoogleCalendarProperties(
        IntegrationMode mode,
        boolean enabled,
        String calendarId,
        String baseUrl,
        String defaultTimezone
) {

    public AssistantGoogleCalendarProperties {
        mode = mode == null ? IntegrationMode.STUB : mode;
        calendarId = calendarId == null || calendarId.isBlank() ? "primary" : calendarId.trim();
        baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "https://www.googleapis.com/calendar/v3"
                : baseUrl.trim();
        defaultTimezone = defaultTimezone == null || defaultTimezone.isBlank()
                ? "Europe/Moscow"
                : defaultTimezone.trim();
    }

    public boolean stubMode() {
        return mode != IntegrationMode.REAL;
    }

    public boolean runtimeEnabled() {
        return enabled || mode == IntegrationMode.REAL;
    }
}
