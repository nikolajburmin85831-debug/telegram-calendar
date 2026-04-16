package io.github.nadya.assistant.adapter.out.persistence.memory;

import io.github.nadya.assistant.ports.out.IdempotencyPort;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryIdempotencyAdapter implements IdempotencyPort {

    private final Set<String> processedKeys = ConcurrentHashMap.newKeySet();

    @Override
    public boolean registerIfAbsent(String key) {
        return processedKeys.add(key);
    }
}
