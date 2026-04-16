package io.github.nadya.assistant.adapter.in.telegram.polling.config;

import java.time.Duration;

public record TelegramPollingProperties(
        boolean enabled,
        String botToken,
        Duration pollInterval,
        int limit
) {

    public TelegramPollingProperties {
        botToken = botToken == null ? "" : botToken;
        pollInterval = pollInterval == null || pollInterval.isNegative() || pollInterval.isZero()
                ? Duration.ofSeconds(5)
                : pollInterval;
        limit = limit <= 0 ? 100 : limit;
    }
}
