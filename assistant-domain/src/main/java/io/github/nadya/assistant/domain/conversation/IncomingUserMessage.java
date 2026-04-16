package io.github.nadya.assistant.domain.conversation;

import io.github.nadya.assistant.domain.common.ChannelType;
import io.github.nadya.assistant.domain.user.UserIdentity;

import java.time.Instant;
import java.util.Objects;

public record IncomingUserMessage(
        String internalMessageId,
        String externalMessageId,
        UserIdentity userId,
        ChannelType channelType,
        String conversationId,
        String text,
        Instant receivedAt
) {

    public IncomingUserMessage {
        internalMessageId = Objects.requireNonNullElse(internalMessageId, "").trim();
        externalMessageId = Objects.requireNonNullElse(externalMessageId, "").trim();
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(channelType, "channelType must not be null");
        conversationId = Objects.requireNonNullElse(conversationId, "").trim();
        text = Objects.requireNonNullElse(text, "").trim();
        receivedAt = Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        if (internalMessageId.isBlank()) {
            throw new IllegalArgumentException("internalMessageId must not be blank");
        }
        if (conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
    }
}
