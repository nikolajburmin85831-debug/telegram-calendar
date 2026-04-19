package io.github.nadya.assistant.adapter.out.gemini.mapper;

import io.github.nadya.assistant.adapter.out.gemini.dto.GeminiGenerateContentResponse;
import io.github.nadya.assistant.domain.common.ConfidenceScore;
import io.github.nadya.assistant.domain.intent.AssistantIntent;
import io.github.nadya.assistant.domain.intent.IntentInterpretation;
import io.github.nadya.assistant.domain.intent.IntentType;

import java.util.*;

public final class GeminiInterpretationMapper {

    public IntentInterpretation map(GeminiGenerateContentResponse response) {
        IntentType intentType = normalizeIntentType(response.intentType());
        Map<String, String> entities = normalizeEntities(response.entities());
        List<String> ambiguityMarkers = normalizeAmbiguityMarkers(response.ambiguityMarkers());
        List<String> missingFields = normalizeMissingFields(response.missingFields(), intentType, entities, ambiguityMarkers);
        boolean safeToExecute = intentType != IntentType.UNKNOWN
                && ambiguityMarkers.isEmpty()
                && missingFields.isEmpty();

        return new IntentInterpretation(
                new AssistantIntent(intentType, entities),
                new ConfidenceScore(response.confidence()),
                ambiguityMarkers,
                missingFields,
                safeToExecute
        );
    }

    private IntentType normalizeIntentType(String rawIntentType) {
        String normalized = normalizeKey(rawIntentType);
        return switch (normalized) {
            case "createcalendarevent", "calendarevent", "createevent", "appointment", "reminder" ->
                    IntentType.CREATE_CALENDAR_EVENT;
            case "listagenda", "agenda", "showagenda", "dayagenda", "showplans", "listevents" ->
                    IntentType.LIST_AGENDA;
            default -> IntentType.UNKNOWN;
        };
    }

    private Map<String, String> normalizeEntities(Map<String, String> rawEntities) {
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        rawEntities.forEach((key, value) -> {
            String canonicalKey = canonicalEntityKey(key);
            if (canonicalKey == null || value == null) {
                return;
            }

            String normalizedValue = value.trim();
            if (normalizedValue.isBlank()) {
                return;
            }

            if ("startTime".equals(canonicalKey)) {
                normalizedValue = normalizeTimeValue(normalizedValue);
            }
            if ("allDay".equals(canonicalKey)) {
                normalizedValue = normalizeBooleanValue(normalizedValue);
            }
            normalized.put(canonicalKey, normalizedValue);
        });

        if (normalized.containsKey("startTime") && !normalized.containsKey("allDay")) {
            normalized.put("allDay", "false");
        }
        if (Boolean.parseBoolean(normalized.getOrDefault("allDay", "false"))) {
            normalized.remove("startTime");
        }
        return Map.copyOf(normalized);
    }

    private List<String> normalizeAmbiguityMarkers(List<String> rawAmbiguityMarkers) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String marker : rawAmbiguityMarkers) {
            String canonical = canonicalAmbiguityMarker(marker);
            if (canonical != null) {
                normalized.add(canonical);
            }
        }
        return List.copyOf(normalized);
    }

    private List<String> normalizeMissingFields(
            List<String> rawMissingFields,
            IntentType intentType,
            Map<String, String> entities,
            List<String> ambiguityMarkers
    ) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String field : rawMissingFields) {
            String canonical = canonicalMissingField(field);
            if (canonical != null) {
                normalized.add(canonical);
            }
        }

        if (intentType == IntentType.LIST_AGENDA) {
            if (entities.getOrDefault("agendaRange", "").isBlank()) {
                normalized.add("date");
            }
            return List.copyOf(normalized);
        }

        if (entities.getOrDefault("title", "").isBlank()) {
            normalized.add("title");
        }
        if (entities.getOrDefault("startDate", "").isBlank()) {
            normalized.add("date");
        }
        boolean allDay = Boolean.parseBoolean(entities.getOrDefault("allDay", "false"));
        if (!allDay && entities.getOrDefault("startTime", "").isBlank() && !ambiguityMarkers.contains("time_is_range")) {
            normalized.add("time");
        }

        return List.copyOf(normalized);
    }

    private String canonicalEntityKey(String rawKey) {
        String normalized = normalizeKey(rawKey);
        return switch (normalized) {
            case "title", "summary", "subject", "name", "eventtitle" -> "title";
            case "agendarange", "relativeagendarange", "relativeperiod", "dayrange" -> "agendaRange";
            case "startdate", "date", "day" -> "startDate";
            case "starttime", "time", "hour" -> "startTime";
            case "enddate" -> "endDate";
            case "endtime" -> "endTime";
            case "durationminutes", "duration" -> "durationMinutes";
            case "allday", "wholeday", "fullday" -> "allDay";
            case "timezone", "tz" -> "timezone";
            case "recurrence", "recurrencerule", "rrule" -> "recurrenceRule";
            case "location", "place", "venue" -> "location";
            default -> null;
        };
    }

    private String canonicalMissingField(String rawField) {
        String normalized = normalizeKey(rawField);
        return switch (normalized) {
            case "title", "summary", "subject", "name", "eventtitle" -> "title";
            case "date", "startdate", "day" -> "date";
            case "time", "starttime", "hour" -> "time";
            default -> null;
        };
    }

    private String canonicalAmbiguityMarker(String rawMarker) {
        String normalized = normalizeKey(rawMarker);
        return switch (normalized) {
            case "timeisrange", "timerange", "vaguetime", "approximatetime" -> "time_is_range";
            case "date", "daterange", "vaguedate" -> "date";
            case "title", "name", "subject" -> "title";
            default -> null;
        };
    }

    private String normalizeTimeValue(String rawValue) {
        String trimmed = rawValue.trim();
        if (trimmed.matches("\\d{1,2}")) {
            return "%02d:00".formatted(Integer.parseInt(trimmed));
        }
        if (trimmed.matches("\\d{1,2}:\\d{2}")) {
            String[] parts = trimmed.split(":");
            return "%02d:%s".formatted(Integer.parseInt(parts[0]), parts[1]);
        }
        return trimmed;
    }

    private String normalizeBooleanValue(String rawValue) {
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true", "yes", "1" -> "true";
            case "false", "no", "0" -> "false";
            default -> rawValue.trim();
        };
    }

    private String normalizeKey(String rawKey) {
        if (rawKey == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(rawKey.length());
        for (char character : rawKey.trim().toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(character)) {
                builder.append(character);
            }
        }
        return builder.toString();
    }
}
