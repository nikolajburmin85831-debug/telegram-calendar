package io.github.nadya.assistant.ports.out;

import io.github.nadya.assistant.domain.user.UserIdentity;

import java.time.Instant;
import java.util.Objects;

public record AuditEntry(
        UserIdentity userId,
        String conversationId,
        String eventType,
        Instant occurredAt,
        String details
) {

    public AuditEntry {
        Objects.requireNonNull(userId, "userId must not be null");
        conversationId = Objects.requireNonNullElse(conversationId, "").trim();
        eventType = Objects.requireNonNullElse(eventType, "").trim();
        occurredAt = Objects.requireNonNullElseGet(occurredAt, Instant::now);
        details = Objects.requireNonNullElse(details, "").trim();
    }
}
