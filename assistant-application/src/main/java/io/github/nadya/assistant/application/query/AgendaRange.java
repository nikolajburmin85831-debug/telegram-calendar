package io.github.nadya.assistant.application.query;

import java.util.Locale;
import java.util.Optional;

public enum AgendaRange {
    TODAY("today", "Сегодня", "На сегодня событий нет."),
    TOMORROW("tomorrow", "Завтра", "На завтра ничего не запланировано.");

    private final String entityValue;
    private final String titleLabel;
    private final String emptySummary;

    AgendaRange(String entityValue, String titleLabel, String emptySummary) {
        this.entityValue = entityValue;
        this.titleLabel = titleLabel;
        this.emptySummary = emptySummary;
    }

    public static Optional<AgendaRange> fromEntityValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }

        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "today" -> Optional.of(TODAY);
            case "tomorrow" -> Optional.of(TOMORROW);
            default -> Optional.empty();
        };
    }

    public String entityValue() {
        return entityValue;
    }

    public String titleLabel() {
        return titleLabel;
    }

    public String emptySummary() {
        return emptySummary;
    }
}
