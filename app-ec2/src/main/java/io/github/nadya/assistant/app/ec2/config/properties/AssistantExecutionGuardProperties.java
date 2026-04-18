package io.github.nadya.assistant.app.ec2.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "assistant.execution-guard")
public record AssistantExecutionGuardProperties(
        int maxTitleLength,
        int maxDescriptionLength,
        int maxEventDurationHours,
        int confirmationHorizonDays
) {

    public AssistantExecutionGuardProperties {
        maxTitleLength = maxTitleLength <= 0 ? 120 : maxTitleLength;
        maxDescriptionLength = maxDescriptionLength <= 0 ? 500 : maxDescriptionLength;
        maxEventDurationHours = maxEventDurationHours <= 0 ? 24 : maxEventDurationHours;
        confirmationHorizonDays = confirmationHorizonDays <= 0 ? 180 : confirmationHorizonDays;
    }

    public Duration maxEventDuration() {
        return Duration.ofHours(maxEventDurationHours);
    }
}
