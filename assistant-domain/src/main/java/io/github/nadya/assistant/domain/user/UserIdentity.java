package io.github.nadya.assistant.domain.user;

import java.util.Objects;

public record UserIdentity(String value) {

    public UserIdentity {
        Objects.requireNonNull(value, "value must not be null");
    }
}
