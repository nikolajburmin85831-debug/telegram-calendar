package io.github.nadya.assistant.domain.conversation;

public enum ConversationStatus {
    IDLE,
    AWAITING_DATE,
    AWAITING_TIME,
    AWAITING_CONFIRMATION,
    EXECUTING_ACTION,
    COMPLETED,
    FAILED,
    CANCELLED
}
