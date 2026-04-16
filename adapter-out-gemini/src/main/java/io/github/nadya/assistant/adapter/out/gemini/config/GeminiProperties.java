package io.github.nadya.assistant.adapter.out.gemini.config;

public record GeminiProperties(String model, String apiKey, boolean stubMode) {

    public GeminiProperties {
        model = model == null || model.isBlank() ? "gemini-2.5-flash" : model;
        apiKey = apiKey == null ? "" : apiKey;
    }
}
