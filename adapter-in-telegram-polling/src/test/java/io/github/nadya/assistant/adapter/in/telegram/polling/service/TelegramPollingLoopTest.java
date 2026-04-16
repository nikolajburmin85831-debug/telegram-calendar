package io.github.nadya.assistant.adapter.in.telegram.polling.service;

import io.github.nadya.assistant.adapter.in.telegram.polling.client.StubTelegramPollingClient;
import io.github.nadya.assistant.adapter.in.telegram.polling.config.TelegramPollingProperties;
import io.github.nadya.assistant.adapter.in.telegram.polling.mapper.TelegramUpdateMapper;
import io.github.nadya.assistant.domain.conversation.IncomingUserMessage;
import io.github.nadya.assistant.domain.execution.ExecutionResult;
import io.github.nadya.assistant.ports.in.HandleIncomingMessageUseCase;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelegramPollingLoopTest {

    @Test
    void shouldNormalizeStubTelegramUpdateAndPassItToUseCase() {
        RecordingHandleIncomingMessageUseCase useCase = new RecordingHandleIncomingMessageUseCase();
        TelegramPollingLoop loop = new TelegramPollingLoop(
                new StubTelegramPollingClient(new TelegramPollingProperties(
                        true,
                        "",
                        Duration.ofSeconds(1),
                        10,
                        true,
                        List.of("Напомни завтра в 10:00 позвонить маме"),
                        42L,
                        101L
                )),
                new TelegramUpdateMapper(),
                useCase,
                new TelegramPollingProperties(true, "", Duration.ofSeconds(1), 10, true, List.of("x"), 42L, 101L)
        );

        int handledCount = loop.pollOnce();
        int secondPollCount = loop.pollOnce();

        assertEquals(1, handledCount);
        assertEquals(0, secondPollCount);
        assertEquals(1, useCase.messages.size());
        assertEquals("telegram-user:42", useCase.messages.get(0).userId().value());
        assertEquals("telegram-chat:101", useCase.messages.get(0).conversationId());
    }

    private static final class RecordingHandleIncomingMessageUseCase implements HandleIncomingMessageUseCase {
        private final List<IncomingUserMessage> messages = new ArrayList<>();

        @Override
        public ExecutionResult handle(IncomingUserMessage message) {
            messages.add(message);
            return ExecutionResult.skipped("recorded");
        }
    }
}
