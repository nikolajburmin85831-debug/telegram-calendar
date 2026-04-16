package io.github.nadya.assistant.domain.conversation;

import io.github.nadya.assistant.domain.intent.IntentInterpretation;

import java.time.Instant;
import java.util.Objects;

public record PendingAction(
        String pendingActionId,
        IncomingUserMessage sourceMessage,
        IntentInterpretation interpretation,
        Instant updatedAt
) {

    public PendingAction {
        pendingActionId = Objects.requireNonNullElse(pendingActionId, "").trim();
        if (pendingActionId.isBlank()) {
            throw new IllegalArgumentException("pendingActionId must not be blank");
        }
        Objects.requireNonNull(sourceMessage, "sourceMessage must not be null");
        Objects.requireNonNull(interpretation, "interpretation must not be null");
        updatedAt = Objects.requireNonNullElseGet(updatedAt, Instant::now);
    }

    public PendingAction withInterpretation(IntentInterpretation nextInterpretation) {
        return new PendingAction(pendingActionId, sourceMessage, nextInterpretation, Instant.now());
    }
}
