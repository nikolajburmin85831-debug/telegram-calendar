package io.github.nadya.assistant.domain.execution;

import io.github.nadya.assistant.domain.conversation.ClarificationRequest;
import io.github.nadya.assistant.domain.conversation.PendingConfirmation;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record ExecutionGuardResult(
        ExecutionGuardOutcome outcome,
        RiskLevel riskLevel,
        List<GuardViolation> violations,
        ClarificationRequest clarificationRequest,
        PendingConfirmation pendingConfirmation,
        String rejectionReason
) {

    public ExecutionGuardResult {
        Objects.requireNonNull(outcome, "outcome must not be null");
        riskLevel = riskLevel == null ? RiskLevel.LOW : riskLevel;
        violations = List.copyOf(violations == null ? List.of() : violations);
        rejectionReason = rejectionReason == null ? "" : rejectionReason.trim();
    }

    public static ExecutionGuardResult allow() {
        return new ExecutionGuardResult(ExecutionGuardOutcome.ALLOW, RiskLevel.LOW, List.of(), null, null, "");
    }

    public static ExecutionGuardResult requireConfirmation(
            RiskLevel riskLevel,
            List<GuardViolation> violations,
            PendingConfirmation pendingConfirmation
    ) {
        return new ExecutionGuardResult(
                ExecutionGuardOutcome.REQUIRE_CONFIRMATION,
                riskLevel,
                violations,
                null,
                pendingConfirmation,
                ""
        );
    }

    public static ExecutionGuardResult requireClarification(
            RiskLevel riskLevel,
            List<GuardViolation> violations,
            ClarificationRequest clarificationRequest
    ) {
        return new ExecutionGuardResult(
                ExecutionGuardOutcome.REQUIRE_CLARIFICATION,
                riskLevel,
                violations,
                clarificationRequest,
                null,
                ""
        );
    }

    public static ExecutionGuardResult reject(
            RiskLevel riskLevel,
            List<GuardViolation> violations,
            String rejectionReason
    ) {
        return new ExecutionGuardResult(
                ExecutionGuardOutcome.REJECT,
                riskLevel,
                violations,
                null,
                null,
                rejectionReason
        );
    }

    public String auditSummary() {
        String codes = violations.stream()
                .map(GuardViolation::code)
                .collect(Collectors.joining(","));
        return "outcome=%s;risk=%s;violations=%s".formatted(outcome, riskLevel, codes);
    }
}
