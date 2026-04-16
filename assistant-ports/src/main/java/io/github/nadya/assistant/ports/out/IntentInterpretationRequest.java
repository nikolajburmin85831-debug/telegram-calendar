package io.github.nadya.assistant.ports.out;

import io.github.nadya.assistant.domain.conversation.ConversationState;
import io.github.nadya.assistant.domain.conversation.IncomingUserMessage;
import io.github.nadya.assistant.domain.user.UserContext;

import java.util.Objects;

public record IntentInterpretationRequest(
        IncomingUserMessage message,
        UserContext userContext,
        ConversationState conversationState
) {

    public IntentInterpretationRequest {
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(userContext, "userContext must not be null");
        Objects.requireNonNull(conversationState, "conversationState must not be null");
    }
}
