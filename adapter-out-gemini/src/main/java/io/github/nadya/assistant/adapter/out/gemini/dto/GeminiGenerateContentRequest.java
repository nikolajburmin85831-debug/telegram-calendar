package io.github.nadya.assistant.adapter.out.gemini.dto;

import java.util.Objects;

public record GeminiGenerateContentRequest(String model, String prompt) {

    public GeminiGenerateContentRequest {
        model = Objects.requireNonNullElse(model, "").trim();
        prompt = Objects.requireNonNullElse(prompt, "").trim();
    }
}
