package io.github.nadya.assistant.application.handler;

import io.github.nadya.assistant.application.result.HandlingOutcome;
import io.github.nadya.assistant.domain.conversation.ClarificationRequest;
import io.github.nadya.assistant.domain.conversation.ConversationState;
import io.github.nadya.assistant.domain.conversation.ConversationStatus;
import io.github.nadya.assistant.domain.conversation.PendingAction;
import io.github.nadya.assistant.domain.execution.ExecutionResult;

public final class ClarificationHandler {

    public HandlingOutcome handle(
            ConversationState currentState,
            ClarificationRequest clarificationRequest,
            PendingAction pendingAction
    ) {
        ConversationStatus nextStatus = determineNextStatus(clarificationRequest);
        ClarificationRequest persistedRequest = new ClarificationRequest(
                clarificationRequest.reason(),
                clarificationRequest.missingFields(),
                clarificationRequest.userFacingQuestion(),
                pendingAction.pendingActionId()
        );
        ConversationState nextState = currentState.awaiting(nextStatus, persistedRequest, pendingAction);
        ExecutionResult executionResult = ExecutionResult.clarificationRequested(
                persistedRequest.userFacingQuestion(),
                "clarification_requested",
                nextStatus.name()
        );

        return new HandlingOutcome(executionResult, nextState);
    }

    private ConversationStatus determineNextStatus(ClarificationRequest clarificationRequest) {
        if (clarificationRequest.missingFields().contains("date") || "date".equals(clarificationRequest.reason())) {
            return ConversationStatus.AWAITING_DATE;
        }
        if (clarificationRequest.missingFields().contains("time")
                || clarificationRequest.missingFields().contains("time_is_range")
                || "time".equals(clarificationRequest.reason())
                || "time_is_range".equals(clarificationRequest.reason())) {
            return ConversationStatus.AWAITING_TIME;
        }
        return ConversationStatus.IDLE;
    }
}
