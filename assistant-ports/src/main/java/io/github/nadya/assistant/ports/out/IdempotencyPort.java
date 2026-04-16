package io.github.nadya.assistant.ports.out;

public interface IdempotencyPort {

    boolean registerIfAbsent(String key);
}
