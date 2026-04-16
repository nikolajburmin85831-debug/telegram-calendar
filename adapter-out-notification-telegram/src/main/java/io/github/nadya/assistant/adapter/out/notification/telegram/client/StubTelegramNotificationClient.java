package io.github.nadya.assistant.adapter.out.notification.telegram.client;

public final class StubTelegramNotificationClient implements TelegramNotificationClient {

    @Override
    public SendMessageResponse send(SendMessageRequest request) {
        // TODO: replace with Telegram sendMessage HTTP call while keeping transport DTOs inside this adapter.
        return new SendMessageResponse(true);
    }
}
