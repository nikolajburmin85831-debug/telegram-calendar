package io.github.nadya.assistant.adapter.in.telegram.polling.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.nadya.assistant.adapter.in.telegram.polling.config.TelegramPollingProperties;
import io.github.nadya.assistant.adapter.in.telegram.polling.dto.TelegramUpdateDto;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class TelegramBotApiPollingClient implements TelegramPollingClient {

    private static final Duration EXTRA_HTTP_TIMEOUT = Duration.ofSeconds(5);

    private final TelegramPollingProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TelegramBotApiPollingClient(TelegramPollingProperties properties) {
        this(
                properties,
                HttpClient.newBuilder()
                        .connectTimeout(properties.httpRequestTimeout(EXTRA_HTTP_TIMEOUT))
                        .build(),
                new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        );
    }

    TelegramBotApiPollingClient(
            TelegramPollingProperties properties,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<TelegramUpdateDto> getUpdates(long offset, int limit) {
        if (!properties.enabled() || properties.botToken().isBlank()) {
            return List.of();
        }

        String requestBody = serializeRequest(new TelegramGetUpdatesRequest(offset, limit, properties.timeoutSeconds()));
        HttpRequest request = HttpRequest.newBuilder(buildUri("getUpdates"))
                .timeout(properties.httpRequestTimeout(EXTRA_HTTP_TIMEOUT))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Telegram getUpdates failed with status " + response.statusCode());
            }

            TelegramGetUpdatesEnvelope envelope = objectMapper.readValue(response.body(), TelegramGetUpdatesEnvelope.class);
            if (!envelope.ok()) {
                throw new IllegalStateException("Telegram getUpdates response was not accepted");
            }

            return envelope.result().stream()
                    .map(this::toUpdateDto)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingLong(TelegramUpdateDto::updateId))
                    .toList();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Telegram getUpdates response could not be parsed", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Telegram getUpdates request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Telegram getUpdates request was interrupted", exception);
        }
    }

    private URI buildUri(String method) {
        return URI.create(properties.apiBaseUrl() + "/bot" + properties.botToken() + "/" + method);
    }

    private String serializeRequest(TelegramGetUpdatesRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Telegram getUpdates request could not be serialized", exception);
        }
    }

    private TelegramUpdateDto toUpdateDto(TelegramUpdateRaw update) {
        if (update == null || update.message() == null || update.message().from() == null || update.message().chat() == null) {
            return null;
        }

        Instant messageInstant = update.message().date() <= 0
                ? Instant.now()
                : Instant.ofEpochSecond(update.message().date());

        return new TelegramUpdateDto(
                update.updateId(),
                new TelegramUpdateDto.TelegramMessageDto(
                        update.message().messageId(),
                        new TelegramUpdateDto.TelegramUserDto(
                                update.message().from().id(),
                                update.message().from().username(),
                                update.message().from().languageCode()
                        ),
                        new TelegramUpdateDto.TelegramChatDto(
                                update.message().chat().id(),
                                update.message().chat().type()
                        ),
                        update.message().text(),
                        messageInstant
                )
        );
    }

    record TelegramGetUpdatesRequest(long offset, int limit, int timeout) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TelegramGetUpdatesEnvelope(boolean ok, List<TelegramUpdateRaw> result) {
        TelegramGetUpdatesEnvelope {
            result = result == null ? List.of() : List.copyOf(result);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TelegramUpdateRaw(@JsonProperty("update_id") long updateId, TelegramMessageRaw message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TelegramMessageRaw(
            @JsonProperty("message_id") long messageId,
            TelegramUserRaw from,
            TelegramChatRaw chat,
            long date,
            String text
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TelegramUserRaw(long id, String username, @JsonProperty("language_code") String languageCode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TelegramChatRaw(long id, String type) {
    }
}
