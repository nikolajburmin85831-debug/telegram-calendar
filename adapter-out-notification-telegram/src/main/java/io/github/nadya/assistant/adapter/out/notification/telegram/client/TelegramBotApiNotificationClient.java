package io.github.nadya.assistant.adapter.out.notification.telegram.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class TelegramBotApiNotificationClient implements TelegramNotificationClient {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);

    private final String apiBaseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TelegramBotApiNotificationClient(String apiBaseUrl) {
        this(
                apiBaseUrl,
                HttpClient.newBuilder()
                        .connectTimeout(HTTP_TIMEOUT)
                        .build(),
                new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        );
    }

    TelegramBotApiNotificationClient(
            String apiBaseUrl,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.apiBaseUrl = apiBaseUrl;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public SendMessageResponse send(SendMessageRequest request) {
        HttpRequest httpRequest = HttpRequest.newBuilder(buildUri(request.botToken()))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(serializeRequest(request), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Telegram sendMessage failed with status " + response.statusCode());
            }

            TelegramSendMessageEnvelope envelope = objectMapper.readValue(response.body(), TelegramSendMessageEnvelope.class);
            if (!envelope.ok()) {
                throw new IllegalStateException("Telegram sendMessage response was not accepted");
            }

            return new SendMessageResponse(true);
        } catch (IOException exception) {
            throw new IllegalStateException("Telegram sendMessage response could not be parsed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Telegram sendMessage request was interrupted", exception);
        }
    }

    private URI buildUri(String botToken) {
        return URI.create(apiBaseUrl + "/bot" + botToken + "/sendMessage");
    }

    private String serializeRequest(SendMessageRequest request) {
        try {
            return objectMapper.writeValueAsString(new TelegramSendMessageRequest(request.chatId(), request.text()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Telegram sendMessage request could not be serialized", exception);
        }
    }

    record TelegramSendMessageRequest(String chat_id, String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TelegramSendMessageEnvelope(boolean ok) {
    }
}
