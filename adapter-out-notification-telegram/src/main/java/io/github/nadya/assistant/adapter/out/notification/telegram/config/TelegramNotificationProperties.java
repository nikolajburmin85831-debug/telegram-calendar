package io.github.nadya.assistant.adapter.out.notification.telegram.config;

public record TelegramNotificationProperties(boolean enabled, String botToken) {

    public TelegramNotificationProperties {
        botToken = botToken == null ? "" : botToken;
    }
}
