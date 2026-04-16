package io.github.nadya.assistant.adapter.out.persistence.memory;

import io.github.nadya.assistant.domain.conversation.ConversationState;
import io.github.nadya.assistant.ports.out.ConversationStatePort;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryConversationStateAdapter implements ConversationStatePort {

    private final ConcurrentMap<String, ConversationState> storage = new ConcurrentHashMap<>();

    @Override
    public Optional<ConversationState> findByConversationId(String conversationId) {
        return Optional.ofNullable(storage.get(conversationId));
    }

    @Override
    public ConversationState save(ConversationState conversationState) {
        storage.put(conversationState.conversationId(), conversationState);
        return conversationState;
    }
}
