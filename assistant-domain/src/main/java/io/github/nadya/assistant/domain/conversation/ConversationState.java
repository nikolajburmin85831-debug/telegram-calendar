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
        PendingAction pendingAction,
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
        return new ConversationState(conversationId, userId, ConversationStatus.IDLE, null, null, null, Instant.now());
    }

    public boolean isAwaitingClarification() {
        return (status == ConversationStatus.AWAITING_DATE || status == ConversationStatus.AWAITING_TIME)
                && clarificationRequest != null
                && pendingAction != null;
    }

    public boolean isAwaitingConfirmation() {
        return status == ConversationStatus.AWAITING_CONFIRMATION
                && pendingConfirmation != null
                && pendingAction != null;
    }

    public ConversationState awaiting(
            ConversationStatus nextStatus,
            ClarificationRequest nextClarificationRequest,
            PendingAction nextPendingAction
    ) {
        return new ConversationState(
                conversationId,
                userId,
                nextStatus,
                nextClarificationRequest,
                null,
                nextPendingAction,
                Instant.now()
        );
    }

    public ConversationState awaitingConfirmation(
            PendingConfirmation nextPendingConfirmation,
            PendingAction nextPendingAction
    ) {
        return new ConversationState(
                conversationId,
                userId,
                ConversationStatus.AWAITING_CONFIRMATION,
                null,
                nextPendingConfirmation,
                nextPendingAction,
                Instant.now()
        );
    }

    public ConversationState executing() {
        return new ConversationState(
                conversationId,
                userId,
                ConversationStatus.EXECUTING_ACTION,
                null,
                null,
                pendingAction,
                Instant.now()
        );
    }

    public ConversationState completed() {
        return new ConversationState(conversationId, userId, ConversationStatus.COMPLETED, null, null, null, Instant.now());
    }

    public ConversationState failed() {
        return new ConversationState(conversationId, userId, ConversationStatus.FAILED, null, null, null, Instant.now());
    }

    public ConversationState cancelled() {
        return new ConversationState(conversationId, userId, ConversationStatus.CANCELLED, null, null, null, Instant.now());
    }
}
