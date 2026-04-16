package io.github.nadya.assistant.ports.in;

import io.github.nadya.assistant.domain.conversation.IncomingUserMessage;
import io.github.nadya.assistant.domain.execution.ExecutionResult;

public interface ResumeConversationUseCase {

    ExecutionResult resume(IncomingUserMessage message);
}
