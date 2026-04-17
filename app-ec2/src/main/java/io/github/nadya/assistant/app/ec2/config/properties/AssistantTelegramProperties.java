package io.github.nadya.assistant.app.ec2.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "assistant.telegram")
public record AssistantTelegramProperties(
        IntegrationMode mode,
        String botToken,
        String apiBaseUrl,
        Polling polling,
        Notification notification
) {

    public AssistantTelegramProperties {
        mode = mode == null ? IntegrationMode.STUB : mode;
        botToken = botToken == null ? "" : botToken.trim();
        apiBaseUrl = apiBaseUrl == null || apiBaseUrl.isBlank() ? "https://api.telegram.org" : apiBaseUrl.trim();
        polling = polling == null ? new Polling(false, 1_500L, 20, 100, List.of(), 10001L, 20001L) : polling;
        notification = notification == null ? new Notification(false) : notification;
    }

    public boolean stubMode() {
        return mode != IntegrationMode.REAL;
    }

    public record Polling(
            boolean enabled,
            long fixedDelayMs,
            int timeoutSeconds,
            int limit,
            List<String> sampleMessages,
            long stubUserId,
            long stubChatId
    ) {
        public Polling {
            fixedDelayMs = fixedDelayMs <= 0 ? 1_500L : fixedDelayMs;
            timeoutSeconds = timeoutSeconds < 0 ? 20 : timeoutSeconds;
            limit = limit <= 0 ? 100 : limit;
            sampleMessages = List.copyOf(sampleMessages == null ? List.of() : sampleMessages);
            stubUserId = stubUserId <= 0 ? 10001L : stubUserId;
            stubChatId = stubChatId <= 0 ? 20001L : stubChatId;
        }
    }

    public record Notification(boolean enabled) {
    }
}
