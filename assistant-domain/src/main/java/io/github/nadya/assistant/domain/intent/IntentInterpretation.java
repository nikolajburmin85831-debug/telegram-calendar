package io.github.nadya.assistant.domain.intent;

import io.github.nadya.assistant.domain.common.ConfidenceScore;

import java.util.List;
import java.util.Objects;

public record IntentInterpretation(
        AssistantIntent assistantIntent,
        ConfidenceScore confidenceScore,
        List<String> ambiguityMarkers,
        List<String> missingFields,
        boolean safeToExecute
) {

    public IntentInterpretation {
        Objects.requireNonNull(assistantIntent, "assistantIntent must not be null");
        Objects.requireNonNull(confidenceScore, "confidenceScore must not be null");
        ambiguityMarkers = List.copyOf(ambiguityMarkers == null ? List.of() : ambiguityMarkers);
        missingFields = List.copyOf(missingFields == null ? List.of() : missingFields);
    }

    public IntentType intentType() {
        return assistantIntent.type();
    }
}
