package io.github.nadya.assistant.domain.execution;

public enum ExecutionGuardOutcome {
    ALLOW,
    REQUIRE_CONFIRMATION,
    REQUIRE_CLARIFICATION,
    REJECT
}
