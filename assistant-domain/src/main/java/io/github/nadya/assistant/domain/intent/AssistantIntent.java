package io.github.nadya.assistant.domain.intent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record AssistantIntent(IntentType type, Map<String, String> entities) {

    public AssistantIntent {
        Objects.requireNonNull(type, "type must not be null");
        entities = Map.copyOf(entities == null ? Map.of() : new LinkedHashMap<>(entities));
    }
}
