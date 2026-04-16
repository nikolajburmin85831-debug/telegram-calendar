package io.github.nadya.assistant.ports.out;

import java.time.Instant;
import java.util.Objects;

public record ScheduledTask(String taskKey, Instant executeAt, String description) {

    public ScheduledTask {
        taskKey = Objects.requireNonNullElse(taskKey, "").trim();
        executeAt = Objects.requireNonNull(executeAt, "executeAt must not be null");
        description = Objects.requireNonNullElse(description, "").trim();
    }
}
