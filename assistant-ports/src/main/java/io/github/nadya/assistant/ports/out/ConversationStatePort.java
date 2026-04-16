package io.github.nadya.assistant.ports.out;

import io.github.nadya.assistant.domain.conversation.ConversationState;

import java.util.Optional;

public interface ConversationStatePort {

    Optional<ConversationState> findByConversationId(String conversationId);

    ConversationState save(ConversationState conversationState);
}
