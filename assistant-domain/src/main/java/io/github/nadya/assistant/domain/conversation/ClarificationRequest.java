package io.github.nadya.assistant.domain.conversation;

import java.util.List;
import java.util.Objects;

public record ClarificationRequest(
        String reason,
        List<String> missingFields,
        String userFacingQuestion,
        String pendingActionReference
) {

    public ClarificationRequest {
        reason = Objects.requireNonNullElse(reason, "").trim();
        missingFields = List.copyOf(missingFields == null ? List.of() : missingFields);
        userFacingQuestion = Objects.requireNonNullElse(userFacingQuestion, "").trim();
        pendingActionReference = Objects.requireNonNullElse(pendingActionReference, "").trim();
    }
}
