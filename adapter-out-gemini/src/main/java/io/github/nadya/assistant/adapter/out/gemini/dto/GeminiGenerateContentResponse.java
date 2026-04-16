package io.github.nadya.assistant.adapter.out.gemini.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record GeminiGenerateContentResponse(
        String intentType,
        Map<String, String> entities,
        double confidence,
        List<String> ambiguityMarkers,
        List<String> missingFields,
        boolean safeToExecute
) {

    public GeminiGenerateContentResponse {
        intentType = intentType == null ? "UNKNOWN" : intentType;
        entities = Map.copyOf(entities == null ? Map.of() : new LinkedHashMap<>(entities));
        ambiguityMarkers = List.copyOf(ambiguityMarkers == null ? List.of() : ambiguityMarkers);
        missingFields = List.copyOf(missingFields == null ? List.of() : missingFields);
    }
}
