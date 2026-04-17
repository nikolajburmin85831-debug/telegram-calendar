package io.github.nadya.assistant.adapter.in.telegram.polling.config;

import java.time.Duration;

import java.util.List;

public record TelegramPollingProperties(
        boolean enabled,
        String botToken,
        Duration pollInterval,
        int timeoutSeconds,
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
        timeoutSeconds = timeoutSeconds < 0 ? 20 : timeoutSeconds;
        limit = limit <= 0 ? 100 : limit;
        sampleMessages = List.copyOf(sampleMessages == null ? List.of() : sampleMessages);
        stubUserId = stubUserId <= 0 ? 10001L : stubUserId;
        stubChatId = stubChatId <= 0 ? 20001L : stubChatId;
        apiBaseUrl = apiBaseUrl == null || apiBaseUrl.isBlank() ? "https://api.telegram.org" : apiBaseUrl.trim();
    }

    public Duration httpRequestTimeout(Duration extraTimeout) {
        Duration extra = extraTimeout == null || extraTimeout.isNegative() ? Duration.ZERO : extraTimeout;
        Duration longPollWindow = Duration.ofSeconds(Math.max(timeoutSeconds, 0));
        Duration baseline = pollInterval.compareTo(longPollWindow) > 0 ? pollInterval : longPollWindow;
        return baseline.plus(extra);
    }
}
