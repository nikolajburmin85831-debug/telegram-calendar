package io.github.nadya.assistant.adapter.out.notification.telegram.config;

public record TelegramNotificationProperties(
        boolean enabled,
        String botToken,
        boolean stubMode,
        String apiBaseUrl
) {

    public TelegramNotificationProperties {
        botToken = botToken == null ? "" : botToken;
        apiBaseUrl = apiBaseUrl == null || apiBaseUrl.isBlank() ? "https://api.telegram.org" : apiBaseUrl.trim();
    }
}
