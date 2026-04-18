package io.github.nadya.assistant.application.service;

import java.time.Duration;
import java.util.Objects;

public record CalendarExecutionGuardSettings(
        int maxTitleLength,
        int maxDescriptionLength,
        Duration maxEventDuration,
        int confirmationHorizonDays
) {

    public CalendarExecutionGuardSettings {
        maxTitleLength = maxTitleLength <= 0 ? 120 : maxTitleLength;
        maxDescriptionLength = maxDescriptionLength <= 0 ? 500 : maxDescriptionLength;
        maxEventDuration = Objects.requireNonNullElse(maxEventDuration, Duration.ofHours(24));
        if (maxEventDuration.isZero() || maxEventDuration.isNegative()) {
            maxEventDuration = Duration.ofHours(24);
        }
        confirmationHorizonDays = confirmationHorizonDays <= 0 ? 180 : confirmationHorizonDays;
    }
}
