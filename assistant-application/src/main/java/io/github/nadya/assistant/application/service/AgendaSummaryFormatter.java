package io.github.nadya.assistant.application.service;

import io.github.nadya.assistant.application.result.AgendaResult;
import io.github.nadya.assistant.domain.calendar.CalendarAgendaEvent;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class AgendaSummaryFormatter {

    private static final Locale RUSSIAN = Locale.forLanguageTag("ru");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMMM", RUSSIAN);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public String format(AgendaResult result) {
        if (result.events().isEmpty()) {
            return result.query().range().emptySummary();
        }

        String header = "%s, %s".formatted(
                result.query().range().titleLabel(),
                DATE_FORMATTER.format(result.query().dateRange().startInclusive())
        );

        List<String> lines = result.events().stream()
                .sorted(Comparator.comparing(CalendarAgendaEvent::start))
                .map(this::formatEventLine)
                .collect(Collectors.toList());

        return header + ":\n" + String.join("\n", lines);
    }

    private String formatEventLine(CalendarAgendaEvent event) {
        String timing = event.allDay()
                ? "Весь день"
                : formatTimedRange(event);
        String suffix = event.location().isBlank() ? "" : " (" + event.location() + ")";
        return "%s - %s%s".formatted(timing, event.title(), suffix);
    }

    private String formatTimedRange(CalendarAgendaEvent event) {
        String start = TIME_FORMATTER.format(event.start());
        String end = TIME_FORMATTER.format(event.end());
        if (start.equals(end)) {
            return start;
        }
        return start + "-" + end;
    }
}
