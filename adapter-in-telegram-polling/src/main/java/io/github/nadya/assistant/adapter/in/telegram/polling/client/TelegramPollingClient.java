package io.github.nadya.assistant.adapter.in.telegram.polling.client;

import io.github.nadya.assistant.adapter.in.telegram.polling.dto.TelegramUpdateDto;

import java.util.List;

public interface TelegramPollingClient {

    List<TelegramUpdateDto> getUpdates(long offset, int limit);
}
