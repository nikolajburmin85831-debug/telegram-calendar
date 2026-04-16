package io.github.nadya.assistant.application.handler;

import io.github.nadya.assistant.application.result.HandlingOutcome;
import io.github.nadya.assistant.domain.conversation.ConversationState;
import io.github.nadya.assistant.domain.conversation.PendingConfirmation;
import io.github.nadya.assistant.domain.conversation.PendingAction;
import io.github.nadya.assistant.domain.execution.ExecutionResult;

public final class PendingConfirmationHandler {

    public HandlingOutcome handle(
            ConversationState currentState,
            PendingConfirmation pendingConfirmation,
            PendingAction pendingAction
    ) {
        PendingConfirmation persistedConfirmation = new PendingConfirmation(
                pendingAction.pendingActionId(),
                pendingConfirmation.userId(),
                pendingConfirmation.actionSummary(),
                pendingConfirmation.confirmationPrompt(),
                pendingConfirmation.expiresAt(),
                pendingAction.pendingActionId()
        );
        ConversationState nextState = currentState.awaitingConfirmation(persistedConfirmation, pendingAction);
        ExecutionResult executionResult = ExecutionResult.confirmationRequested(
                persistedConfirmation.confirmationPrompt(),
                "confirmation_requested",
                persistedConfirmation.pendingActionId()
        );

        return new HandlingOutcome(executionResult, nextState);
    }

    public HandlingOutcome handleInvalidResponse(
            ConversationState currentState,
            PendingConfirmation pendingConfirmation
    ) {
        ExecutionResult executionResult = ExecutionResult.confirmationRequested(
                "Не поняла ответ. Напишите \"да\", \"нет\" или \"отмена\".",
                "confirmation_response_invalid",
                pendingConfirmation.pendingActionId()
        );
        return new HandlingOutcome(executionResult, currentState);
    }
}
