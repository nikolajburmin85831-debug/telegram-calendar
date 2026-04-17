package io.github.nadya.assistant.adapter.in.telegram.polling.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.nadya.assistant.adapter.in.telegram.polling.client.TelegramBotApiPollingClient;
import io.github.nadya.assistant.adapter.in.telegram.polling.config.TelegramPollingProperties;
import io.github.nadya.assistant.adapter.in.telegram.polling.dto.TelegramUpdateDto;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramBotApiPollingClientTest {

    @Test
    void shouldUseLongPollWindowForHttpTimeout() {
        TelegramPollingProperties longPollProperties = new TelegramPollingProperties(
                true,
                "test-token",
                Duration.ofMillis(1500),
                20,
                100,
                false,
                List.of(),
                10001L,
                20001L,
                "https://api.telegram.org"
        );
        TelegramPollingProperties shortPollProperties = new TelegramPollingProperties(
                true,
                "test-token",
                Duration.ofSeconds(5),
                0,
                100,
                false,
                List.of(),
                10001L,
                20001L,
                "https://api.telegram.org"
        );

        assertEquals(Duration.ofSeconds(25), longPollProperties.httpRequestTimeout(Duration.ofSeconds(5)));
        assertEquals(Duration.ofSeconds(10), shortPollProperties.httpRequestTimeout(Duration.ofSeconds(5)));
    }

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

    @Test
    void shouldReportTransportFailureInsteadOfParseFailure() throws Exception {
        TelegramBotApiPollingClient client = buildClient(new TelegramPollingProperties(
                true,
                "test-token",
                Duration.ofSeconds(1),
                20,
                100,
                false,
                List.of(),
                10001L,
                20001L,
                "https://api.telegram.org"
        ));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> client.getUpdates(0L, 1));

        assertEquals("Telegram getUpdates request failed", exception.getMessage());
        assertInstanceOf(HttpConnectTimeoutException.class, exception.getCause());
    }

    private TelegramBotApiPollingClient buildClient(TelegramPollingProperties properties) throws Exception {
        Constructor<TelegramBotApiPollingClient> constructor = TelegramBotApiPollingClient.class.getDeclaredConstructor(
                TelegramPollingProperties.class,
                HttpClient.class,
                ObjectMapper.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(
                properties,
                new FailingHttpClient(),
                new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        );
    }

    private static final class FailingHttpClient extends HttpClient {

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.of(Duration.ofSeconds(1));
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
            throw new HttpConnectTimeoutException("HTTP connect timed out");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler
        ) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("Not used in this test"));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("Not used in this test"));
        }

        @Override
        public WebSocket.Builder newWebSocketBuilder() {
            throw new UnsupportedOperationException("Not used in this test");
        }
    }
}
