package io.github.nadya.assistant.adapter.out.gemini.config;

public record GeminiProperties(
        String model,
        String apiKey,
        boolean stubMode,
        String baseUrl,
        boolean fallbackToStubOnError
) {

    public GeminiProperties {
        model = model == null || model.isBlank() ? "gemini-2.5-flash" : model;
        apiKey = apiKey == null ? "" : apiKey;
        baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "https://generativelanguage.googleapis.com"
                : baseUrl.trim();
    }
}
