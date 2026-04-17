package io.github.nadya.assistant.adapter.out.google.calendar.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class GoogleCalendarOAuthSupport implements GoogleAccessTokenProvider {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration EXPIRY_SKEW = Duration.ofSeconds(30);

    private final GoogleOAuthProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AtomicReference<CachedAccessToken> cachedAccessToken;

    public GoogleCalendarOAuthSupport(GoogleOAuthProperties properties) {
        this(
                properties,
                HttpClient.newBuilder()
                        .connectTimeout(HTTP_TIMEOUT)
                        .build(),
                new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false),
                new AtomicReference<>(null)
        );
    }

    GoogleCalendarOAuthSupport(
            GoogleOAuthProperties properties,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            AtomicReference<CachedAccessToken> cachedAccessToken
    ) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.cachedAccessToken = Objects.requireNonNull(cachedAccessToken, "cachedAccessToken must not be null");
    }

    @Override
    public boolean isReady() {
        return !properties.accessToken().isBlank() || hasRefreshTokenCredentials();
    }

    @Override
    public String getAccessToken() {
        if (!properties.accessToken().isBlank() && !hasRefreshTokenCredentials()) {
            return properties.accessToken();
        }

        CachedAccessToken current = cachedAccessToken.get();
        if (current != null && !current.isExpired()) {
            return current.value();
        }

        synchronized (cachedAccessToken) {
            CachedAccessToken latest = cachedAccessToken.get();
            if (latest != null && !latest.isExpired()) {
                return latest.value();
            }

            CachedAccessToken refreshed = refreshAccessToken();
            cachedAccessToken.set(refreshed);
            return refreshed.value();
        }
    }

    @Override
    public void invalidate() {
        if (hasRefreshTokenCredentials()) {
            cachedAccessToken.set(null);
        }
    }

    private boolean hasRefreshTokenCredentials() {
        return !properties.clientId().isBlank()
                && !properties.clientSecret().isBlank()
                && !properties.refreshToken().isBlank();
    }

    private CachedAccessToken refreshAccessToken() {
        if (!hasRefreshTokenCredentials()) {
            if (!properties.accessToken().isBlank()) {
                return new CachedAccessToken(properties.accessToken(), Instant.MAX);
            }
            throw new IllegalStateException("Google OAuth refresh credentials are not configured");
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.tokenUrl()))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(buildRefreshRequestBody(), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Google OAuth token refresh failed with status " + response.statusCode());
            }

            TokenRefreshResponse refreshResponse = objectMapper.readValue(response.body(), TokenRefreshResponse.class);
            if (refreshResponse.accessToken() == null || refreshResponse.accessToken().isBlank()) {
                throw new IllegalStateException("Google OAuth token refresh response did not include access token");
            }

            long expiresInSeconds = refreshResponse.expiresIn() <= 0 ? 300L : refreshResponse.expiresIn();
            Instant expiresAt = Instant.now().plusSeconds(expiresInSeconds).minus(EXPIRY_SKEW);
            return new CachedAccessToken(refreshResponse.accessToken(), expiresAt);
        } catch (IOException exception) {
            throw new IllegalStateException("Google OAuth token refresh response could not be parsed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google OAuth token refresh request was interrupted", exception);
        }
    }

    private String buildRefreshRequestBody() {
        StringBuilder requestBody = new StringBuilder()
                .append("grant_type=refresh_token")
                .append("&client_id=").append(urlEncode(properties.clientId()))
                .append("&client_secret=").append(urlEncode(properties.clientSecret()))
                .append("&refresh_token=").append(urlEncode(properties.refreshToken()));

        if (!properties.scopes().isEmpty()) {
            requestBody.append("&scope=").append(urlEncode(String.join(" ", properties.scopes())));
        }
        return requestBody.toString();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    record CachedAccessToken(String value, Instant expiresAt) {
        boolean isExpired() {
            return expiresAt.isBefore(Instant.now());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenRefreshResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken,
            @com.fasterxml.jackson.annotation.JsonProperty("expires_in") long expiresIn
    ) {
    }
}
