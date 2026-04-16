package io.github.nadya.assistant.adapter.out.notification.telegram.client;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class StubTelegramNotificationClient implements TelegramNotificationClient {

    private static final System.Logger LOGGER = System.getLogger(StubTelegramNotificationClient.class.getName());

    private final List<SendMessageRequest> sentRequests = new CopyOnWriteArrayList<>();

    @Override
    public SendMessageResponse send(SendMessageRequest request) {
        sentRequests.add(request);
        LOGGER.log(System.Logger.Level.INFO, "Stub Telegram notification to {0}: {1}", request.chatId(), request.text());
        // TODO: replace with Telegram sendMessage HTTP call while keeping transport DTOs inside this adapter.
        return new SendMessageResponse(true);
    }

    public List<SendMessageRequest> sentRequests() {
        return List.copyOf(sentRequests);
    }
}
