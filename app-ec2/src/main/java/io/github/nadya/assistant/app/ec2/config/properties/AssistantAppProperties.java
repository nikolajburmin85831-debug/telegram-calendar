package io.github.nadya.assistant.app.ec2.config.properties;

import io.github.nadya.assistant.domain.user.ConfirmationPreference;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "assistant.app")
public record AssistantAppProperties(
        String defaultTimezone,
        String defaultLanguage,
        boolean requireConfirmation,
        int defaultReminderDurationMinutes,
        int defaultMeetingDurationMinutes,
        int idempotencyTtlHours
) {

    public AssistantAppProperties {
        defaultTimezone = defaultTimezone == null || defaultTimezone.isBlank() ? "Europe/Moscow" : defaultTimezone.trim();
        defaultLanguage = defaultLanguage == null || defaultLanguage.isBlank() ? "ru" : defaultLanguage.trim();
        defaultReminderDurationMinutes = defaultReminderDurationMinutes <= 0 ? 15 : defaultReminderDurationMinutes;
        defaultMeetingDurationMinutes = defaultMeetingDurationMinutes <= 0 ? 30 : defaultMeetingDurationMinutes;
        idempotencyTtlHours = idempotencyTtlHours <= 0 ? 24 : idempotencyTtlHours;
    }

    public ConfirmationPreference defaultConfirmationPreference() {
        return requireConfirmation ? ConfirmationPreference.REQUIRE_CONFIRMATION : ConfirmationPreference.AUTO_EXECUTE;
    }

    public Duration defaultEventDuration() {
        return Duration.ofMinutes(defaultMeetingDurationMinutes);
    }

    public Duration idempotencyTtl() {
        return Duration.ofHours(idempotencyTtlHours);
    }
}
