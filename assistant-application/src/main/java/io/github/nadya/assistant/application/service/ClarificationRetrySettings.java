package io.github.nadya.assistant.application.service;

public record ClarificationRetrySettings(
        int maxInvalidAttempts
) {

    public ClarificationRetrySettings {
        maxInvalidAttempts = maxInvalidAttempts <= 0 ? 2 : maxInvalidAttempts;
    }
}
