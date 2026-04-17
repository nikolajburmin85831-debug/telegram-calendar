package io.github.nadya.assistant.adapter.out.notification.telegram.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramBotApiNotificationClientTest {

    @Test
    void shouldSendMessageThroughTelegramBotApi() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        try {
            server.createContext("/bottest-token/sendMessage", exchange -> {
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

                byte[] responseBody = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseBody.length);
                try (var outputStream = exchange.getResponseBody()) {
                    outputStream.write(responseBody);
                }
            });
            server.start();

            TelegramBotApiNotificationClient client = new TelegramBotApiNotificationClient(
                    "http://127.0.0.1:" + server.getAddress().getPort()
            );

            TelegramNotificationClient.SendMessageResponse response = client.send(
                    new TelegramNotificationClient.SendMessageRequest("12345", "Привет из теста", "test-token")
            );

            assertTrue(response.accepted());
            assertEquals("{\"chat_id\":\"12345\",\"text\":\"Привет из теста\"}", requestBody.get());
        } finally {
            server.stop(0);
        }
    }
}
