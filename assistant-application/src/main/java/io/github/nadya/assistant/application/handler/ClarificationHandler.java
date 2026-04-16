package io.github.nadya.assistant.application.handler;

import io.github.nadya.assistant.application.result.HandlingOutcome;
import io.github.nadya.assistant.domain.conversation.ClarificationRequest;
import io.github.nadya.assistant.domain.conversation.ConversationState;
import io.github.nadya.assistant.domain.conversation.ConversationStatus;
import io.github.nadya.assistant.domain.execution.ExecutionResult;

public final class ClarificationHandler {

    public HandlingOutcome handle(ConversationState currentState, ClarificationRequest clarificationRequest) {
        ConversationStatus nextStatus = determineNextStatus(clarificationRequest);
        ConversationState nextState = currentState.awaiting(nextStatus, clarificationRequest);
        ExecutionResult executionResult = ExecutionResult.clarificationRequested(
                clarificationRequest.userFacingQuestion(),
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
