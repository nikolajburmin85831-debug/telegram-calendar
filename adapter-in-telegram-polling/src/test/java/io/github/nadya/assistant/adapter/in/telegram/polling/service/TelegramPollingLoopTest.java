package io.github.nadya.assistant.adapter.in.telegram.polling.service;

import io.github.nadya.assistant.adapter.in.telegram.polling.client.StubTelegramPollingClient;
import io.github.nadya.assistant.adapter.in.telegram.polling.client.TelegramPollingClient;
import io.github.nadya.assistant.adapter.in.telegram.polling.config.TelegramPollingProperties;
import io.github.nadya.assistant.adapter.in.telegram.polling.dto.TelegramUpdateDto;
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
                        101L,
                        "https://api.telegram.org"
                )),
                new TelegramUpdateMapper(),
                useCase,
                new TelegramPollingProperties(
                        true,
                        "",
                        Duration.ofSeconds(1),
                        10,
                        true,
                        List.of("x"),
                        42L,
                        101L,
                        "https://api.telegram.org"
                )
        );

        int handledCount = loop.pollOnce();
        int secondPollCount = loop.pollOnce();

        assertEquals(1, handledCount);
        assertEquals(0, secondPollCount);
        assertEquals(1, useCase.messages.size());
        assertEquals("telegram-user:42", useCase.messages.get(0).userId().value());
        assertEquals("telegram-chat:101", useCase.messages.get(0).conversationId());
    }

    @Test
    void shouldAdvanceOffsetUsingHighestUpdateIdEvenIfClientReturnsUnorderedUpdates() {
        RecordingHandleIncomingMessageUseCase useCase = new RecordingHandleIncomingMessageUseCase();
        TelegramPollingClient client = new TelegramPollingClient() {
            private boolean firstCall = true;

            @Override
            public List<TelegramUpdateDto> getUpdates(long offset, int limit) {
                if (!firstCall) {
                    assertEquals(8L, offset);
                    return List.of();
                }
                firstCall = false;
                return List.of(
                        update(7L, 102L, "второе"),
                        update(3L, 101L, "первое")
                );
            }
        };

        TelegramPollingLoop loop = new TelegramPollingLoop(
                client,
                new TelegramUpdateMapper(),
                useCase,
                new TelegramPollingProperties(
                        true,
                        "",
                        Duration.ofSeconds(1),
                        10,
                        true,
                        List.of(),
                        42L,
                        101L,
                        "https://api.telegram.org"
                )
        );

        int handledCount = loop.pollOnce();
        int secondPollCount = loop.pollOnce();

        assertEquals(2, handledCount);
        assertEquals(0, secondPollCount);
        assertEquals("первое", useCase.messages.get(0).text());
        assertEquals("второе", useCase.messages.get(1).text());
    }

    private TelegramUpdateDto update(long updateId, long messageId, String text) {
        return new TelegramUpdateDto(
                updateId,
                new TelegramUpdateDto.TelegramMessageDto(
                        messageId,
                        new TelegramUpdateDto.TelegramUserDto(42L, "user", "ru"),
                        new TelegramUpdateDto.TelegramChatDto(101L, "private"),
                        text,
                        java.time.Instant.parse("2026-04-16T20:15:30Z")
                )
        );
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
