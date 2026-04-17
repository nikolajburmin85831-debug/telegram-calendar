package io.github.nadya.assistant.adapter.out.google.calendar.client;

import com.sun.net.httpserver.HttpServer;
import io.github.nadya.assistant.adapter.out.google.calendar.config.GoogleCalendarProperties;
import io.github.nadya.assistant.adapter.out.google.calendar.oauth.GoogleAccessTokenProvider;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpGoogleCalendarClientTest {

    @Test
    void shouldInsertTimedEventThroughGoogleCalendarApi() throws IOException {
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        try {
            server.createContext("/calendars/primary/events", exchange -> {
                authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

                byte[] responseBody = """
                        {"id":"event-123","htmlLink":"https://calendar.google.test/event-123"}
                        """.trim().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseBody.length);
                try (var outputStream = exchange.getResponseBody()) {
                    outputStream.write(responseBody);
                }
            });
            server.start();

            HttpGoogleCalendarClient client = new HttpGoogleCalendarClient(
                    new GoogleCalendarProperties(
                            true,
                            "primary",
                            false,
                            "http://127.0.0.1:" + server.getAddress().getPort()
                    ),
                    staticAccessToken("access-token")
            );

            GoogleCalendarClient.InsertResponse response = client.insert(new GoogleCalendarClient.InsertRequest(
                    "primary",
                    "Демо",
                    "Created by test",
                    "2026-04-17T10:00:00+03:00",
                    "2026-04-17T11:00:00+03:00",
                    false,
                    "Europe/Moscow",
                    "Zoom"
            ));

            assertEquals("Bearer access-token", authorizationHeader.get());
            assertTrue(requestBody.get().contains("\"summary\":\"Демо\""));
            assertTrue(requestBody.get().contains("\"dateTime\":\"2026-04-17T10:00:00+03:00\""));
            assertTrue(requestBody.get().contains("\"timeZone\":\"Europe/Moscow\""));
            assertTrue(requestBody.get().contains("\"location\":\"Zoom\""));
            assertEquals("event-123", response.eventId());
            assertEquals("https://calendar.google.test/event-123", response.htmlLink());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldRetryWithRefreshedAccessTokenAfterUnauthorizedResponse() throws IOException {
        AtomicInteger callCount = new AtomicInteger();
        AtomicReference<String> finalAuthorizationHeader = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        try {
            server.createContext("/calendars/primary/events", exchange -> {
                int currentCall = callCount.incrementAndGet();
                finalAuthorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));

                if (currentCall == 1) {
                    exchange.sendResponseHeaders(401, -1);
                    exchange.close();
                    return;
                }

                byte[] responseBody = """
                        {"id":"event-456","htmlLink":"https://calendar.google.test/event-456"}
                        """.trim().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseBody.length);
                try (var outputStream = exchange.getResponseBody()) {
                    outputStream.write(responseBody);
                }
            });
            server.start();

            CyclingAccessTokenProvider accessTokenProvider = new CyclingAccessTokenProvider("expired-token", "fresh-token");
            HttpGoogleCalendarClient client = new HttpGoogleCalendarClient(
                    new GoogleCalendarProperties(
                            true,
                            "primary",
                            false,
                            "http://127.0.0.1:" + server.getAddress().getPort()
                    ),
                    accessTokenProvider
            );

            GoogleCalendarClient.InsertResponse response = client.insert(new GoogleCalendarClient.InsertRequest(
                    "primary",
                    "Демо",
                    "Created by test",
                    "2026-04-17T10:00:00+03:00",
                    "2026-04-17T11:00:00+03:00",
                    false,
                    "Europe/Moscow",
                    "Zoom"
            ));

            assertEquals(2, callCount.get());
            assertEquals(1, accessTokenProvider.invalidations());
            assertEquals("Bearer fresh-token", finalAuthorizationHeader.get());
            assertEquals("event-456", response.eventId());
        } finally {
            server.stop(0);
        }
    }

    private GoogleAccessTokenProvider staticAccessToken(String token) {
        return new GoogleAccessTokenProvider() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public String getAccessToken() {
                return token;
            }

            @Override
            public void invalidate() {
            }
        };
    }

    private static final class CyclingAccessTokenProvider implements GoogleAccessTokenProvider {
        private final String firstToken;
        private final String refreshedToken;
        private boolean invalidated;
        private int invalidations;

        private CyclingAccessTokenProvider(String firstToken, String refreshedToken) {
            this.firstToken = firstToken;
            this.refreshedToken = refreshedToken;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public String getAccessToken() {
            return invalidated ? refreshedToken : firstToken;
        }

        @Override
        public void invalidate() {
            invalidated = true;
            invalidations++;
        }

        int invalidations() {
            return invalidations;
        }
    }
}
