package io.github.nadya.assistant.adapter.out.notification.telegram.service;

import io.github.nadya.assistant.adapter.out.notification.telegram.client.TelegramNotificationClient;
import io.github.nadya.assistant.adapter.out.notification.telegram.config.TelegramNotificationProperties;
import io.github.nadya.assistant.adapter.out.notification.telegram.mapper.TelegramNotificationMapper;
import io.github.nadya.assistant.ports.out.NotificationCommand;
import io.github.nadya.assistant.ports.out.NotificationPort;

public final class TelegramNotificationAdapter implements NotificationPort {

    private final TelegramNotificationClient telegramNotificationClient;
    private final TelegramNotificationMapper mapper;
    private final TelegramNotificationProperties properties;

    public TelegramNotificationAdapter(
            TelegramNotificationClient telegramNotificationClient,
            TelegramNotificationMapper mapper,
            TelegramNotificationProperties properties
    ) {
        this.telegramNotificationClient = telegramNotificationClient;
        this.mapper = mapper;
        this.properties = properties;
    }

    @Override
    public void send(NotificationCommand command) {
        if (!properties.enabled() || properties.botToken().isBlank() || command.text().isBlank()) {
            return;
        }

        telegramNotificationClient.send(mapper.map(command, properties));
    }
}
