package io.github.nadya.assistant.adapter.out.google.calendar.oauth;

import java.util.List;

public record GoogleOAuthProperties(
        String clientId,
        String clientSecret,
        String refreshToken,
        String accessToken,
        String tokenUrl,
        List<String> scopes,
        String credentialSource
) {

    public GoogleOAuthProperties {
        clientId = clientId == null ? "" : clientId.trim();
        clientSecret = clientSecret == null ? "" : clientSecret.trim();
        refreshToken = refreshToken == null ? "" : refreshToken.trim();
        accessToken = accessToken == null ? "" : accessToken.trim();
        tokenUrl = tokenUrl == null || tokenUrl.isBlank()
                ? "https://oauth2.googleapis.com/token"
                : tokenUrl.trim();
        scopes = List.copyOf(scopes == null ? List.of() : scopes);
        credentialSource = credentialSource == null || credentialSource.isBlank()
                ? "env"
                : credentialSource.trim();
    }
}
