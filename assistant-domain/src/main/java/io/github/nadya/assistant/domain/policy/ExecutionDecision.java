package io.github.nadya.assistant.domain.policy;

import io.github.nadya.assistant.domain.conversation.ClarificationRequest;
import io.github.nadya.assistant.domain.conversation.PendingConfirmation;

public record ExecutionDecision(
        ExecutionOutcome outcome,
        ClarificationRequest clarificationRequest,
        PendingConfirmation pendingConfirmation,
        String rejectionReason
) {

    public static ExecutionDecision executeNow() {
        return new ExecutionDecision(ExecutionOutcome.EXECUTE_NOW, null, null, null);
    }

    public static ExecutionDecision askForClarification(ClarificationRequest clarificationRequest) {
        return new ExecutionDecision(ExecutionOutcome.ASK_FOR_CLARIFICATION, clarificationRequest, null, null);
    }

    public static ExecutionDecision askForConfirmation(PendingConfirmation pendingConfirmation) {
        return new ExecutionDecision(ExecutionOutcome.ASK_FOR_CONFIRMATION, null, pendingConfirmation, null);
    }

    public static ExecutionDecision reject(String rejectionReason) {
        return new ExecutionDecision(ExecutionOutcome.REJECT, null, null, rejectionReason);
    }
}
