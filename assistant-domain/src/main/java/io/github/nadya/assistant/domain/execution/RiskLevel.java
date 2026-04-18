package io.github.nadya.assistant.domain.execution;

public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH;

    public static RiskLevel max(RiskLevel left, RiskLevel right) {
        return left.ordinal() >= right.ordinal() ? left : right;
    }
}
