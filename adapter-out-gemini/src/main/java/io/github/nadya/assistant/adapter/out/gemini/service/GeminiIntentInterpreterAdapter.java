package io.github.nadya.assistant.adapter.out.gemini.service;

import io.github.nadya.assistant.adapter.out.gemini.config.GeminiProperties;
import io.github.nadya.assistant.adapter.out.gemini.dto.GeminiGenerateContentRequest;
import io.github.nadya.assistant.adapter.out.gemini.dto.GeminiGenerateContentResponse;
import io.github.nadya.assistant.adapter.out.gemini.mapper.GeminiInterpretationMapper;
import io.github.nadya.assistant.domain.intent.IntentInterpretation;
import io.github.nadya.assistant.ports.out.IntentInterpretationRequest;
import io.github.nadya.assistant.ports.out.IntentInterpreterPort;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GeminiIntentInterpreterAdapter implements IntentInterpreterPort {

    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern LOCAL_DATE_PATTERN = Pattern.compile("\\b(\\d{1,2})\\.(\\d{1,2})(?:\\.(\\d{4}))?\\b");
    private static final Pattern TIME_PATTERN = Pattern.compile("(?iu)(?:\\b(?:в|at)\\s*)?(\\d{1,2})(?::(\\d{2}))\\b");
    private static final Pattern HOUR_ONLY_PATTERN = Pattern.compile("(?iu)\\b(?:в|at)\\s*(\\d{1,2})\\b");
    private static final Pattern LOCATION_PATTERN = Pattern.compile("(?iu)\\b(?:в|at)\\s+(офисе|zoom|online|онлайн)\\b");

    private final GeminiProperties properties;
    private final GeminiInterpretationMapper interpretationMapper;

    public GeminiIntentInterpreterAdapter(GeminiProperties properties, GeminiInterpretationMapper interpretationMapper) {
        this.properties = properties;
        this.interpretationMapper = interpretationMapper;
    }

    @Override
    public IntentInterpretation interpret(IntentInterpretationRequest request) {
        GeminiGenerateContentRequest contentRequest = new GeminiGenerateContentRequest(
                properties.model(),
                buildPrompt(request)
        );

        GeminiGenerateContentResponse rawResponse = execute(contentRequest, request);
        return interpretationMapper.map(rawResponse);
    }

    private String buildPrompt(IntentInterpretationRequest request) {
        return """
                Interpret the incoming message into a canonical internal intent.
                Message: %s
                Conversation status: %s
                Preferred timezone: %s
                """.formatted(
                request.message().text(),
                request.conversationState().status(),
                request.userContext().preferredTimezone().value()
        );
    }

    private GeminiGenerateContentResponse execute(
            GeminiGenerateContentRequest contentRequest,
            IntentInterpretationRequest request
    ) {
        if (properties.stubMode() || properties.apiKey().isBlank()) {
            return simulateGeminiResponse(request);
        }

        // TODO: replace heuristic implementation with a real Gemini API call and keep the raw response inside this adapter.
        return simulateGeminiResponse(request);
    }

    private GeminiGenerateContentResponse simulateGeminiResponse(IntentInterpretationRequest request) {
        String originalText = request.message().text().trim();
        String normalizedText = originalText.toLowerCase(Locale.ROOT);

        if (!looksLikeCalendarIntent(normalizedText)) {
            return new GeminiGenerateContentResponse(
                    "UNKNOWN",
                    Map.of(),
                    0.25d,
                    List.of("unsupported_intent"),
                    List.of(),
                    false
            );
        }

        LinkedHashMap<String, String> entities = new LinkedHashMap<>();
        ArrayList<String> ambiguityMarkers = new ArrayList<>();
        ArrayList<String> missingFields = new ArrayList<>();

        String title = extractTitle(originalText);
        if (title == null || title.isBlank()) {
            missingFields.add("title");
        } else {
            entities.put("title", title);
        }

        LocalDate startDate = extractDate(normalizedText, request);
        if (startDate == null) {
            missingFields.add("date");
        } else {
            entities.put("startDate", startDate.toString());
        }

        boolean allDay = containsAny(normalizedText, "весь день", "на весь день", "all day");
        if (allDay) {
            entities.put("allDay", "true");
        } else {
            LocalTime startTime = extractTime(normalizedText);
            if (startTime == null) {
                if (containsAny(normalizedText, "утром", "днем", "днём", "вечером", "morning", "afternoon", "evening")) {
                    ambiguityMarkers.add("time_is_range");
                } else {
                    missingFields.add("time");
                }
            } else {
                entities.put("startTime", startTime.toString());
                entities.put("allDay", "false");
            }
        }

        String location = extractLocation(originalText);
        if (location != null && !location.isBlank()) {
            entities.put("location", location);
        }

        boolean safeToExecute = missingFields.isEmpty() && ambiguityMarkers.isEmpty();
        double confidence = safeToExecute ? 0.90d : 0.68d;

        return new GeminiGenerateContentResponse(
                "CREATE_CALENDAR_EVENT",
                entities,
                confidence,
                ambiguityMarkers,
                missingFields,
                safeToExecute
        );
    }

    private boolean looksLikeCalendarIntent(String normalizedText) {
        return containsAny(
                normalizedText,
                "встреч",
                "созвон",
                "календар",
                "event",
                "meeting",
                "appointment",
                "call",
                "напомни",
                "remind",
                "запланиру",
                "schedule",
                "добавь",
                "создай"
        );
    }

    private String extractTitle(String originalText) {
        String cleaned = originalText;
        for (String phrase : List.of(
                "создай", "добавь", "запланируй", "напомни",
                "schedule", "create", "add",
                "meeting", "event",
                "встречу", "встреча", "созвон",
                "в календарь", "напоминание",
                "сегодня", "завтра", "today", "tomorrow",
                "весь день", "all day",
                "утром", "днем", "днём", "вечером",
                "morning", "afternoon", "evening"
        )) {
            cleaned = stripPhraseIgnoreCase(cleaned, phrase);
        }

        cleaned = cleaned
                .replaceAll("(?iu)(?:в|at)\\s*\\d{1,2}(?::\\d{2})?", " ")
                .replaceAll("\\d{1,2}\\.\\d{1,2}(?:\\.\\d{4})?", " ")
                .replaceAll("\\d{4}-\\d{2}-\\d{2}", " ")
                .replaceAll("(?iu)(?:в|at)\\s+(офисе|zoom|online|онлайн)", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return cleaned.isBlank() ? null : cleaned;
    }

    private LocalDate extractDate(String normalizedText, IntentInterpretationRequest request) {
        LocalDate today = request.message()
                .receivedAt()
                .atZone(request.userContext().preferredTimezone().toZoneId())
                .toLocalDate();
        if (containsAny(normalizedText, "завтра", "tomorrow")) {
            return today.plusDays(1);
        }
        if (containsAny(normalizedText, "сегодня", "today")) {
            return today;
        }

        Matcher isoMatcher = ISO_DATE_PATTERN.matcher(normalizedText);
        if (isoMatcher.find()) {
            return LocalDate.parse(isoMatcher.group(1));
        }

        Matcher localMatcher = LOCAL_DATE_PATTERN.matcher(normalizedText);
        if (localMatcher.find()) {
            int day = Integer.parseInt(localMatcher.group(1));
            int month = Integer.parseInt(localMatcher.group(2));
            int year = localMatcher.group(3) == null ? today.getYear() : Integer.parseInt(localMatcher.group(3));
            return LocalDate.of(year, month, day);
        }

        return null;
    }

    private LocalTime extractTime(String normalizedText) {
        Matcher exactTimeMatcher = TIME_PATTERN.matcher(normalizedText);
        if (exactTimeMatcher.find()) {
            int hour = Integer.parseInt(exactTimeMatcher.group(1));
            int minute = Integer.parseInt(exactTimeMatcher.group(2));
            return LocalTime.of(hour, minute);
        }

        Matcher hourOnlyMatcher = HOUR_ONLY_PATTERN.matcher(normalizedText);
        if (hourOnlyMatcher.find()) {
            int hour = Integer.parseInt(hourOnlyMatcher.group(1));
            return LocalTime.of(hour, 0);
        }

        return null;
    }

    private String extractLocation(String originalText) {
        Matcher matcher = LOCATION_PATTERN.matcher(originalText);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private boolean containsAny(String normalizedText, String... candidates) {
        for (String candidate : candidates) {
            if (normalizedText.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String stripPhraseIgnoreCase(String text, String phrase) {
        return text.replaceAll("(?iu)" + Pattern.quote(phrase), " ");
    }
}
