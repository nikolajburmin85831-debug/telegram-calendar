package io.github.nadya.assistant.app.ec2.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "assistant.gemini")
public record AssistantGeminiProperties(
        IntegrationMode mode,
        String model,
        String apiKey,
        String baseUrl,
        boolean fallbackToStubOnError
) {

    public AssistantGeminiProperties {
        mode = mode == null ? IntegrationMode.STUB : mode;
        model = model == null ? "" : model.trim();
        apiKey = apiKey == null ? "" : apiKey.trim();
        baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "https://generativelanguage.googleapis.com"
                : baseUrl.trim();
    }

    public boolean stubMode() {
        return mode != IntegrationMode.REAL;
    }
}
