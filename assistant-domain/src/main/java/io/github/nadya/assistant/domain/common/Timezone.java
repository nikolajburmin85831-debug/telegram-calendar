package io.github.nadya.assistant.domain.common;

import java.time.ZoneId;
import java.util.Objects;

public record Timezone(String value) {

    public Timezone {
        Objects.requireNonNull(value, "value must not be null");
    }

    public ZoneId toZoneId() {
        return ZoneId.of(value);
    }
}
