package io.github.nadya.assistant.adapter.in.telegram.polling.service;

import com.sun.net.httpserver.HttpServer;
import io.github.nadya.assistant.adapter.in.telegram.polling.client.TelegramBotApiPollingClient;
import io.github.nadya.assistant.adapter.in.telegram.polling.config.TelegramPollingProperties;
import io.github.nadya.assistant.adapter.in.telegram.polling.dto.TelegramUpdateDto;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramBotApiPollingClientTest {

    @Test
    void shouldFetchAndNormalizeRealTelegramUpdates() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        try {
            server.createContext("/bottest-token/getUpdates", exchange -> {
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

                byte[] responseBody = """
                        {"ok":true,"result":[
                          {"update_id":23,"message":{"message_id":202,"from":{"id":42,"username":"nadya","language_code":"ru"},"chat":{"id":101,"type":"private"},"date":1776492000,"text":"второе"}},
                          {"update_id":17,"message":{"message_id":201,"from":{"id":42,"username":"nadya","language_code":"ru"},"chat":{"id":101,"type":"private"},"date":1776491900,"text":"первое"}}
                        ]}
                        """.trim().getBytes(StandardCharsets.UTF_8);

                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseBody.length);
                try (var outputStream = exchange.getResponseBody()) {
                    outputStream.write(responseBody);
                }
            });
            server.start();

            TelegramBotApiPollingClient client = new TelegramBotApiPollingClient(new TelegramPollingProperties(
                    true,
                    "test-token",
                    Duration.ofSeconds(1),
                    20,
                    100,
                    false,
                    List.of(),
                    10001L,
                    20001L,
                    "http://127.0.0.1:" + server.getAddress().getPort()
            ));

            List<TelegramUpdateDto> updates = client.getUpdates(17L, 2);

            assertTrue(requestBody.get().contains("\"offset\":17"));
            assertTrue(requestBody.get().contains("\"limit\":2"));
            assertTrue(requestBody.get().contains("\"timeout\":20"));
            assertEquals(2, updates.size());
            assertEquals(17L, updates.get(0).updateId());
            assertEquals("первое", updates.get(0).message().text());
            assertEquals(23L, updates.get(1).updateId());
            assertEquals("второе", updates.get(1).message().text());
        } finally {
            server.stop(0);
        }
    }
}
