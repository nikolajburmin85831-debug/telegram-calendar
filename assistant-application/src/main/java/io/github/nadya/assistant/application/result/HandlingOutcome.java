package io.github.nadya.assistant.application.result;

import io.github.nadya.assistant.domain.conversation.ConversationState;
import io.github.nadya.assistant.domain.execution.ExecutionResult;
import io.github.nadya.assistant.ports.out.NotificationCommand;

import java.util.List;
import java.util.Objects;

public record HandlingOutcome(
        ExecutionResult executionResult,
        ConversationState nextConversationState,
        List<NotificationCommand> additionalNotifications
) {

    public HandlingOutcome(ExecutionResult executionResult, ConversationState nextConversationState) {
        this(executionResult, nextConversationState, List.of());
    }

    public HandlingOutcome {
        Objects.requireNonNull(executionResult, "executionResult must not be null");
        Objects.requireNonNull(nextConversationState, "nextConversationState must not be null");
        additionalNotifications = List.copyOf(additionalNotifications == null ? List.of() : additionalNotifications);
    }
}
