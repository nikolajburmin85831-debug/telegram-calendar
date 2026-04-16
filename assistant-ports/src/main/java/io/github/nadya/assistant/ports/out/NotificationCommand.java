package io.github.nadya.assistant.ports.out;

import io.github.nadya.assistant.domain.user.UserIdentity;

import java.util.Objects;

public record NotificationCommand(UserIdentity userId, String conversationId, String text) {

    public NotificationCommand {
        Objects.requireNonNull(userId, "userId must not be null");
        conversationId = Objects.requireNonNullElse(conversationId, "").trim();
        text = Objects.requireNonNullElse(text, "").trim();
    }
}
