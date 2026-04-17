package io.github.nadya.assistant.app.ec2.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "assistant.google-oauth")
public record AssistantGoogleOAuthProperties(
        String clientId,
        String clientSecret,
        String refreshToken,
        String accessToken,
        String tokenUrl,
        String scopes,
        String credentialSource
) {

    public AssistantGoogleOAuthProperties {
        clientId = clientId == null ? "" : clientId.trim();
        clientSecret = clientSecret == null ? "" : clientSecret.trim();
        refreshToken = refreshToken == null ? "" : refreshToken.trim();
        accessToken = accessToken == null ? "" : accessToken.trim();
        tokenUrl = tokenUrl == null || tokenUrl.isBlank()
                ? "https://oauth2.googleapis.com/token"
                : tokenUrl.trim();
        scopes = scopes == null ? "" : scopes.trim();
        credentialSource = credentialSource == null || credentialSource.isBlank()
                ? "env"
                : credentialSource.trim();
    }

    public List<String> parsedScopes() {
        if (scopes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(scopes.split("[,\\s]+"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }
}
