package io.github.nadya.assistant.domain.execution;

public record ExecutionResult(
        boolean success,
        String createdResourceReference,
        String userSummary,
        String auditDetails,
        String nextStepHint
) {

    public static ExecutionResult completed(String createdResourceReference, String userSummary, String auditDetails) {
        return new ExecutionResult(true, createdResourceReference, userSummary, auditDetails, null);
    }

    public static ExecutionResult completedWithoutResource(String userSummary, String auditDetails) {
        return new ExecutionResult(true, null, userSummary, auditDetails, null);
    }

    public static ExecutionResult clarificationRequested(String userSummary, String auditDetails, String nextStepHint) {
        return new ExecutionResult(true, null, userSummary, auditDetails, nextStepHint);
    }

    public static ExecutionResult confirmationRequested(String userSummary, String auditDetails, String nextStepHint) {
        return new ExecutionResult(true, null, userSummary, auditDetails, nextStepHint);
    }

    public static ExecutionResult rejected(String userSummary, String auditDetails) {
        return new ExecutionResult(false, null, userSummary, auditDetails, null);
    }

    public static ExecutionResult failed(String userSummary, String auditDetails) {
        return new ExecutionResult(false, null, userSummary, auditDetails, null);
    }

    public static ExecutionResult cancelled(String userSummary, String auditDetails) {
        return new ExecutionResult(true, null, userSummary, auditDetails, null);
    }

    public static ExecutionResult skipped(String auditDetails) {
        return new ExecutionResult(true, null, null, auditDetails, null);
    }
}
