package io.github.nadya.assistant.domain.conversation;

import io.github.nadya.assistant.domain.user.UserIdentity;

import java.time.Instant;
import java.util.Objects;

public record ConversationState(
        String conversationId,
        UserIdentity userId,
        ConversationStatus status,
        ClarificationRequest clarificationRequest,
        PendingConfirmation pendingConfirmation,
        Instant updatedAt
) {

    public ConversationState {
        conversationId = Objects.requireNonNullElse(conversationId, "").trim();
        Objects.requireNonNull(userId, "userId must not be null");
        status = status == null ? ConversationStatus.IDLE : status;
        updatedAt = Objects.requireNonNullElseGet(updatedAt, Instant::now);
        if (conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
    }

    public static ConversationState idle(String conversationId, UserIdentity userId) {
        return new ConversationState(conversationId, userId, ConversationStatus.IDLE, null, null, Instant.now());
    }

    public ConversationState awaiting(ConversationStatus nextStatus, ClarificationRequest nextClarificationRequest) {
        return new ConversationState(conversationId, userId, nextStatus, nextClarificationRequest, null, Instant.now());
    }

    public ConversationState awaitingConfirmation(PendingConfirmation nextPendingConfirmation) {
        return new ConversationState(
                conversationId,
                userId,
                ConversationStatus.AWAITING_CONFIRMATION,
                null,
                nextPendingConfirmation,
                Instant.now()
        );
    }

    public ConversationState executing() {
        return new ConversationState(conversationId, userId, ConversationStatus.EXECUTING_ACTION, clarificationRequest, pendingConfirmation, Instant.now());
    }

    public ConversationState completed() {
        return new ConversationState(conversationId, userId, ConversationStatus.COMPLETED, null, null, Instant.now());
    }

    public ConversationState failed() {
        return new ConversationState(conversationId, userId, ConversationStatus.FAILED, null, null, Instant.now());
    }
}
