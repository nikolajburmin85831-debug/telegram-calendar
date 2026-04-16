package io.github.nadya.assistant.domain.common;

public record ConfidenceScore(double value) {

    public ConfidenceScore {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("Confidence score must be between 0.0 and 1.0");
        }
    }
}
