package io.github.nadya.assistant.adapter.out.notification.telegram.client;

public interface TelegramNotificationClient {

    SendMessageResponse send(SendMessageRequest request);

    record SendMessageRequest(String chatId, String text, String botToken) {
    }

    record SendMessageResponse(boolean accepted) {
    }
}
