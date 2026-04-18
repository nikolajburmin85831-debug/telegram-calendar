package io.github.nadya.assistant.domain.execution;

import java.util.Objects;

public record GuardViolation(
        String code,
        String message,
        RiskLevel riskLevel
) {

    public GuardViolation {
        code = Objects.requireNonNullElse(code, "").trim();
        message = Objects.requireNonNullElse(message, "").trim();
        riskLevel = riskLevel == null ? RiskLevel.MEDIUM : riskLevel;
        if (code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }
}
