package io.github.nadya.assistant.application.service;

import io.github.nadya.assistant.domain.conversation.IncomingUserMessage;
import io.github.nadya.assistant.domain.conversation.PendingAction;
import io.github.nadya.assistant.domain.intent.IntentInterpretation;

import java.time.Instant;

public final class PendingActionFactory {

    public PendingAction create(IncomingUserMessage sourceMessage, IntentInterpretation interpretation) {
        return new PendingAction(
                "pending-" + sourceMessage.internalMessageId(),
                sourceMessage,
                interpretation,
                Instant.now()
        );
    }
}
