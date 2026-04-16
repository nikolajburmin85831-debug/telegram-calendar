package io.github.nadya.assistant.domain.conversation;

import io.github.nadya.assistant.domain.user.UserIdentity;

import java.time.Instant;
import java.util.Objects;

public record PendingConfirmation(
        String pendingActionId,
        UserIdentity userId,
        String actionSummary,
        String confirmationPrompt,
        Instant expiresAt,
        String payloadReference
) {

    public PendingConfirmation {
        pendingActionId = Objects.requireNonNullElse(pendingActionId, "").trim();
        Objects.requireNonNull(userId, "userId must not be null");
        actionSummary = Objects.requireNonNullElse(actionSummary, "").trim();
        confirmationPrompt = Objects.requireNonNullElse(confirmationPrompt, "").trim();
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        payloadReference = Objects.requireNonNullElse(payloadReference, "").trim();
    }
}
