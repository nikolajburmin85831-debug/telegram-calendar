package io.github.nadya.assistant.application.result;

import io.github.nadya.assistant.domain.conversation.ConversationState;
import io.github.nadya.assistant.domain.execution.ExecutionResult;

import java.util.Objects;

public record HandlingOutcome(ExecutionResult executionResult, ConversationState nextConversationState) {

    public HandlingOutcome {
        Objects.requireNonNull(executionResult, "executionResult must not be null");
        Objects.requireNonNull(nextConversationState, "nextConversationState must not be null");
    }
}
