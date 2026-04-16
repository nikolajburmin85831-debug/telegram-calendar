package io.github.nadya.assistant.ports.out;

import io.github.nadya.assistant.domain.user.UserContext;
import io.github.nadya.assistant.domain.user.UserIdentity;

import java.util.Optional;

public interface UserContextPort {

    Optional<UserContext> findByUserId(UserIdentity userId);

    UserContext save(UserContext userContext);
}
