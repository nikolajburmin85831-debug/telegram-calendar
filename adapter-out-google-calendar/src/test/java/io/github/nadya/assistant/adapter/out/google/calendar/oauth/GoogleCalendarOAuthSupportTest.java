package io.github.nadya.assistant.adapter.out.google.calendar.oauth;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleCalendarOAuthSupportTest {

    @Test
    void shouldRefreshAccessTokenFromRefreshTokenAndCacheItUntilInvalidated() throws IOException {
        AtomicInteger tokenRequests = new AtomicInteger();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        try {
            server.createContext("/token", exchange -> {
                tokenRequests.incrementAndGet();
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

                byte[] responseBody = """
                        {"access_token":"ya29.refreshed-token","expires_in":3600}
                        """.trim().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseBody.length);
                try (var outputStream = exchange.getResponseBody()) {
                    outputStream.write(responseBody);
                }
            });
            server.start();

            GoogleCalendarOAuthSupport support = new GoogleCalendarOAuthSupport(
                    new GoogleOAuthProperties(
                            "client-id",
                            "client-secret",
                            "refresh-token",
                            "",
                            "http://127.0.0.1:" + server.getAddress().getPort() + "/token",
                            List.of("https://www.googleapis.com/auth/calendar"),
                            "test"
                    )
            );

            String firstToken = support.getAccessToken();
            String secondToken = support.getAccessToken();
            support.invalidate();
            String thirdToken = support.getAccessToken();

            assertTrue(support.isReady());
            assertEquals("ya29.refreshed-token", firstToken);
            assertEquals(firstToken, secondToken);
            assertEquals(firstToken, thirdToken);
            assertEquals(2, tokenRequests.get());
            assertTrue(requestBody.get().contains("grant_type=refresh_token"));
            assertTrue(requestBody.get().contains("client_id=client-id"));
            assertTrue(requestBody.get().contains("refresh_token=refresh-token"));
            assertTrue(requestBody.get().contains("scope=https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fcalendar"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldUseStaticAccessTokenWhenRefreshCredentialsAreAbsent() {
        GoogleCalendarOAuthSupport support = new GoogleCalendarOAuthSupport(
                new GoogleOAuthProperties(
                        "",
                        "",
                        "",
                        "static-access-token",
                        "https://oauth2.googleapis.com/token",
                        List.of(),
                        "test"
                )
        );

        assertTrue(support.isReady());
        assertEquals("static-access-token", support.getAccessToken());
    }
}
