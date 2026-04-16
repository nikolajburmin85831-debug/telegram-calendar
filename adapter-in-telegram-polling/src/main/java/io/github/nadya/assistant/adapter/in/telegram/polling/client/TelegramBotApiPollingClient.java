package io.github.nadya.assistant.adapter.in.telegram.polling.client;

import io.github.nadya.assistant.adapter.in.telegram.polling.config.TelegramPollingProperties;
import io.github.nadya.assistant.adapter.in.telegram.polling.dto.TelegramUpdateDto;

import java.util.List;

public final class TelegramBotApiPollingClient implements TelegramPollingClient {

    private final TelegramPollingProperties properties;

    public TelegramBotApiPollingClient(TelegramPollingProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<TelegramUpdateDto> getUpdates(long offset, int limit) {
        if (!properties.enabled() || properties.botToken().isBlank()) {
            return List.of();
        }

        // TODO: call Telegram Bot API getUpdates and map the response into TelegramUpdateDto objects.
        return List.of();
    }
}
