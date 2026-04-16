package io.github.nadya.assistant.application.command;

import io.github.nadya.assistant.domain.conversation.ConversationState;
import io.github.nadya.assistant.domain.conversation.IncomingUserMessage;
import io.github.nadya.assistant.domain.intent.IntentInterpretation;
import io.github.nadya.assistant.domain.user.UserContext;

import java.util.Objects;

public record MessageHandlingContext(
        IncomingUserMessage message,
        UserContext userContext,
        ConversationState conversationState,
        IntentInterpretation interpretation
) {

    public MessageHandlingContext {
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(userContext, "userContext must not be null");
        Objects.requireNonNull(conversationState, "conversationState must not be null");
        Objects.requireNonNull(interpretation, "interpretation must not be null");
    }
}
