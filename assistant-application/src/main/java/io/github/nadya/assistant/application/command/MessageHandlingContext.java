package io.github.nadya.assistant.application.command;

import io.github.nadya.assistant.domain.conversation.ConversationState;
import io.github.nadya.assistant.domain.conversation.IncomingUserMessage;
import io.github.nadya.assistant.domain.execution.ExecutionApproval;
import io.github.nadya.assistant.domain.intent.IntentInterpretation;
import io.github.nadya.assistant.domain.user.UserContext;

import java.util.Objects;

public record MessageHandlingContext(
        IncomingUserMessage triggeringMessage,
        IncomingUserMessage sourceMessage,
        UserContext userContext,
        ConversationState conversationState,
        IntentInterpretation interpretation,
        ExecutionApproval executionApproval
) {

    public MessageHandlingContext(
            IncomingUserMessage triggeringMessage,
            IncomingUserMessage sourceMessage,
            UserContext userContext,
            ConversationState conversationState,
            IntentInterpretation interpretation
    ) {
        this(triggeringMessage, sourceMessage, userContext, conversationState, interpretation, ExecutionApproval.NOT_CONFIRMED);
    }

    public MessageHandlingContext {
        Objects.requireNonNull(triggeringMessage, "triggeringMessage must not be null");
        sourceMessage = sourceMessage == null ? triggeringMessage : sourceMessage;
        Objects.requireNonNull(sourceMessage, "sourceMessage must not be null");
        Objects.requireNonNull(userContext, "userContext must not be null");
        Objects.requireNonNull(conversationState, "conversationState must not be null");
        Objects.requireNonNull(interpretation, "interpretation must not be null");
        executionApproval = executionApproval == null ? ExecutionApproval.NOT_CONFIRMED : executionApproval;
    }
}
