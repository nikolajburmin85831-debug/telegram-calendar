package io.github.nadya.assistant.domain.common;

import java.util.Objects;

public record RecurrenceRule(String value) {

    public RecurrenceRule {
        Objects.requireNonNull(value, "value must not be null");
    }
}
