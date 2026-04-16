package io.github.nadya.assistant.domain.policy;

public enum ExecutionOutcome {
    EXECUTE_NOW,
    ASK_FOR_CLARIFICATION,
    ASK_FOR_CONFIRMATION,
    REJECT,
    DEFER
}
