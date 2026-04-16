package io.github.nadya.assistant.ports.in;

import io.github.nadya.assistant.domain.execution.ExecutionResult;
import io.github.nadya.assistant.domain.user.UserIdentity;

public interface ConfirmPendingActionUseCase {

    ExecutionResult confirm(UserIdentity userId, String conversationId, String confirmationInput);
}
