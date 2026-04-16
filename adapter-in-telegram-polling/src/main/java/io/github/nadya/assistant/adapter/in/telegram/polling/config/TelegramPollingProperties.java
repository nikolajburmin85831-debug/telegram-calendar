package io.github.nadya.assistant.adapter.in.telegram.polling.config;

import java.time.Duration;

import java.util.List;

public record TelegramPollingProperties(
        boolean enabled,
        String botToken,
        Duration pollInterval,
        int limit,
        boolean stubMode,
        List<String> sampleMessages,
        long stubUserId,
        long stubChatId,
        String apiBaseUrl
) {

    public TelegramPollingProperties {
        botToken = botToken == null ? "" : botToken;
        pollInterval = pollInterval == null || pollInterval.isNegative() || pollInterval.isZero()
                ? Duration.ofSeconds(5)
                : pollInterval;
        limit = limit <= 0 ? 100 : limit;
        sampleMessages = List.copyOf(sampleMessages == null ? List.of() : sampleMessages);
        stubUserId = stubUserId <= 0 ? 10001L : stubUserId;
        stubChatId = stubChatId <= 0 ? 20001L : stubChatId;
        apiBaseUrl = apiBaseUrl == null || apiBaseUrl.isBlank() ? "https://api.telegram.org" : apiBaseUrl.trim();
    }
}
