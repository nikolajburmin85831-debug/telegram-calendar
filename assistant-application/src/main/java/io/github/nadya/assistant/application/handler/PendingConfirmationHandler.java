package io.github.nadya.assistant.application.handler;

import io.github.nadya.assistant.application.result.HandlingOutcome;
import io.github.nadya.assistant.domain.conversation.ConversationState;
import io.github.nadya.assistant.domain.conversation.PendingConfirmation;
import io.github.nadya.assistant.domain.execution.ExecutionResult;

public final class PendingConfirmationHandler {

    public HandlingOutcome handle(ConversationState currentState, PendingConfirmation pendingConfirmation) {
        ConversationState nextState = currentState.awaitingConfirmation(pendingConfirmation);
        ExecutionResult executionResult = ExecutionResult.confirmationRequested(
                pendingConfirmation.confirmationPrompt(),
                "confirmation_requested",
                pendingConfirmation.pendingActionId()
        );

        return new HandlingOutcome(executionResult, nextState);
    }
}
