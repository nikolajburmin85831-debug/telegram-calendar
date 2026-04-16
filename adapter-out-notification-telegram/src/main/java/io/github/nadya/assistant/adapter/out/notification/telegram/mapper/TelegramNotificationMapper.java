package io.github.nadya.assistant.adapter.out.notification.telegram.mapper;

import io.github.nadya.assistant.adapter.out.notification.telegram.client.TelegramNotificationClient;
import io.github.nadya.assistant.adapter.out.notification.telegram.config.TelegramNotificationProperties;
import io.github.nadya.assistant.ports.out.NotificationCommand;

public final class TelegramNotificationMapper {

    public TelegramNotificationClient.SendMessageRequest map(
            NotificationCommand command,
            TelegramNotificationProperties properties
    ) {
        String chatId = command.conversationId().startsWith("telegram-chat:")
                ? command.conversationId().substring("telegram-chat:".length())
                : command.conversationId();

        return new TelegramNotificationClient.SendMessageRequest(chatId, command.text(), properties.botToken());
    }
}
