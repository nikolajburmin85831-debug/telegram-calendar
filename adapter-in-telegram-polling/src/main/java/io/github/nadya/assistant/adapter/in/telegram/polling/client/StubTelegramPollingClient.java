package io.github.nadya.assistant.adapter.in.telegram.polling.client;

import io.github.nadya.assistant.adapter.in.telegram.polling.config.TelegramPollingProperties;
import io.github.nadya.assistant.adapter.in.telegram.polling.dto.TelegramUpdateDto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class StubTelegramPollingClient implements TelegramPollingClient {

    private final TelegramPollingProperties properties;

    public StubTelegramPollingClient(TelegramPollingProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<TelegramUpdateDto> getUpdates(long offset, int limit) {
        List<String> sampleMessages = properties.sampleMessages();
        if (!properties.enabled() || sampleMessages.isEmpty()) {
            return List.of();
        }

        int startIndex = Math.max(0, (int) offset - 1);
        int endIndex = Math.min(sampleMessages.size(), startIndex + limit);
        if (startIndex >= endIndex) {
            return List.of();
        }

        ArrayList<TelegramUpdateDto> updates = new ArrayList<>(endIndex - startIndex);
        for (int index = startIndex; index < endIndex; index++) {
            long updateId = index + 1L;
            updates.add(new TelegramUpdateDto(
                    updateId,
                    new TelegramUpdateDto.TelegramMessageDto(
                            updateId,
                            new TelegramUpdateDto.TelegramUserDto(properties.stubUserId(), "stub-user", "ru"),
                            new TelegramUpdateDto.TelegramChatDto(properties.stubChatId(), "private"),
                            sampleMessages.get(index),
                            Instant.now()
                    )
            ));
        }

        return updates;
    }
}
