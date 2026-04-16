package io.github.nadya.assistant.adapter.out.persistence.memory;

import io.github.nadya.assistant.domain.user.UserContext;
import io.github.nadya.assistant.domain.user.UserIdentity;
import io.github.nadya.assistant.ports.out.UserContextPort;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryUserContextAdapter implements UserContextPort {

    private final ConcurrentMap<String, UserContext> storage = new ConcurrentHashMap<>();

    @Override
    public Optional<UserContext> findByUserId(UserIdentity userId) {
        return Optional.ofNullable(storage.get(userId.value()));
    }

    @Override
    public UserContext save(UserContext userContext) {
        storage.put(userContext.userId().value(), userContext);
        return userContext;
    }
}
