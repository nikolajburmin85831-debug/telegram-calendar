package io.github.nadya.assistant.adapter.out.gemini.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.nadya.assistant.adapter.out.gemini.config.GeminiProperties;
import io.github.nadya.assistant.adapter.out.gemini.dto.GeminiGenerateContentRequest;
import io.github.nadya.assistant.adapter.out.gemini.dto.GeminiGenerateContentResponse;
import io.github.nadya.assistant.adapter.out.gemini.mapper.GeminiInterpretationMapper;
import io.github.nadya.assistant.domain.common.ConfidenceScore;
import io.github.nadya.assistant.domain.intent.AssistantIntent;
import io.github.nadya.assistant.domain.intent.IntentInterpretation;
import io.github.nadya.assistant.domain.intent.IntentType;
import io.github.nadya.assistant.ports.out.IntentInterpretationRequest;
import io.github.nadya.assistant.ports.out.IntentInterpreterPort;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
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

    private static final String RUSSIAN_MONTH_NAMES_REGEX =
            "января|январь|февраля|февраль|марта|март|апреля|апрель|мая|май|июня|июнь|июля|июль|августа|август|сентября|сентябрь|октября|октябрь|ноября|ноябрь|декабря|декабрь";
    private static final String NEXT_WEEKDAY_REGEX =
            "(?iu)(?:^|\\s)(?:в\\s+)?следующ(?:ий|ую|ее)\\s+"
                    + "(понедельник|вторник|среда|среду|четверг|пятница|пятницу|суббота|субботу|воскресенье|"
                    + "monday|tuesday|wednesday|thursday|friday|saturday|sunday)(?=$|\\s|[.!?,])";
    private static final String WORD_MONTH_DATE_REGEX =
            "(?iu)(?<![\\p{L}\\p{N}])\\d{1,2}\\s+(?:" + RUSSIAN_MONTH_NAMES_REGEX + ")(?:\\s+\\d{4})?(?:\\s*г(?:ода?)?)?(?![\\p{L}\\p{N}])";
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern LOCAL_DATE_PATTERN = Pattern.compile("\\b(\\d{1,2})\\.(\\d{1,2})(?:\\.(\\d{4}))?\\b");
    private static final Pattern NEXT_WEEKDAY_PATTERN = Pattern.compile(NEXT_WEEKDAY_REGEX);
    private static final Pattern WORD_MONTH_DATE_PATTERN = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])(\\d{1,2})\\s+(" + RUSSIAN_MONTH_NAMES_REGEX + ")(?:\\s+(\\d{4}))?(?:\\s*г(?:ода?)?)?(?![\\p{L}\\p{N}])"
    );
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "(?iu)(?:^|\\s)(?:(?:в|at)\\s*)?(\\d{1,2}):(\\d{2})(?:\\s*(утра|дня|вечера|ночи|am|pm))?(?=$|\\s|[.!?,])"
    );
    private static final Pattern HOUR_ONLY_PATTERN = Pattern.compile(
            "(?iu)(?:^|\\s)(?:в|at)\\s*(\\d{1,2})(?:\\s*(утра|дня|вечера|ночи|am|pm))?(?=$|\\s|[.!?,])"
    );
    private static final Pattern LOCATION_PATTERN = Pattern.compile("(?iu)\\b(?:в|at)\\s+(офисе|zoom|online|онлайн)\\b");
    private static final Pattern CLARIFICATION_TIME_REPLY_PATTERN = Pattern.compile(
            "(?iu)^\\s*(?:в|at)?\\s*(\\d{1,2})(?::(\\d{2}))?(?:\\s*(утра|дня|вечера|ночи|am|pm))?\\s*[.!?,]?\\s*$"
    );
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);
    private static final Map<String, Integer> RUSSIAN_MONTH_NUMBERS = Map.ofEntries(
            Map.entry("января", 1),
            Map.entry("январь", 1),
            Map.entry("февраля", 2),
            Map.entry("февраль", 2),
            Map.entry("марта", 3),
            Map.entry("март", 3),
            Map.entry("апреля", 4),
            Map.entry("апрель", 4),
            Map.entry("мая", 5),
            Map.entry("май", 5),
            Map.entry("июня", 6),
            Map.entry("июнь", 6),
            Map.entry("июля", 7),
            Map.entry("июль", 7),
            Map.entry("августа", 8),
            Map.entry("август", 8),
            Map.entry("сентября", 9),
            Map.entry("сентябрь", 9),
            Map.entry("октября", 10),
            Map.entry("октябрь", 10),
            Map.entry("ноября", 11),
            Map.entry("ноябрь", 11),
            Map.entry("декабря", 12),
            Map.entry("декабрь", 12)
    );
    private static final Map<String, DayOfWeek> WEEKDAY_ALIASES = Map.ofEntries(
            Map.entry("понедельник", DayOfWeek.MONDAY),
            Map.entry("вторник", DayOfWeek.TUESDAY),
            Map.entry("среда", DayOfWeek.WEDNESDAY),
            Map.entry("среду", DayOfWeek.WEDNESDAY),
            Map.entry("четверг", DayOfWeek.THURSDAY),
            Map.entry("пятница", DayOfWeek.FRIDAY),
            Map.entry("пятницу", DayOfWeek.FRIDAY),
            Map.entry("суббота", DayOfWeek.SATURDAY),
            Map.entry("субботу", DayOfWeek.SATURDAY),
            Map.entry("воскресенье", DayOfWeek.SUNDAY),
            Map.entry("monday", DayOfWeek.MONDAY),
            Map.entry("tuesday", DayOfWeek.TUESDAY),
            Map.entry("wednesday", DayOfWeek.WEDNESDAY),
            Map.entry("thursday", DayOfWeek.THURSDAY),
            Map.entry("friday", DayOfWeek.FRIDAY),
            Map.entry("saturday", DayOfWeek.SATURDAY),
            Map.entry("sunday", DayOfWeek.SUNDAY)
    );

    private final GeminiProperties properties;
    private final GeminiInterpretationMapper interpretationMapper;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GeminiIntentInterpreterAdapter(GeminiProperties properties, GeminiInterpretationMapper interpretationMapper) {
        this(
                properties,
                interpretationMapper,
                HttpClient.newBuilder()
                        .connectTimeout(HTTP_TIMEOUT)
                        .build(),
                new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        );
    }

    public GeminiIntentInterpreterAdapter(
            GeminiProperties properties,
            GeminiInterpretationMapper interpretationMapper,
            HttpClient httpClient
    ) {
        this(
                properties,
                interpretationMapper,
                httpClient,
                new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        );
    }

    GeminiIntentInterpreterAdapter(
            GeminiProperties properties,
            GeminiInterpretationMapper interpretationMapper,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.interpretationMapper = interpretationMapper;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public IntentInterpretation interpret(IntentInterpretationRequest request) {
        GeminiGenerateContentRequest contentRequest = new GeminiGenerateContentRequest(
                properties.model(),
                buildPrompt(request)
        );

        GeminiGenerateContentResponse rawResponse = execute(contentRequest, request);
        return stabilizeInterpretation(interpretationMapper.map(rawResponse), request);
    }

    private String buildPrompt(IntentInterpretationRequest request) {
        return """
                Interpret the incoming message into a canonical internal intent for a calendar assistant.
                Return JSON only with this exact shape:
                {
                  "intentType": "CREATE_CALENDAR_EVENT|LIST_AGENDA|UNKNOWN",
                  "entities": {
                    "title": "...",
                    "startDate": "YYYY-MM-DD",
                    "startTime": "HH:mm",
                    "allDay": "true|false",
                    "location": "...",
                    "agendaRange": "today|tomorrow"
                  },
                  "confidence": 0.0,
                  "ambiguityMarkers": [],
                  "missingFields": [],
                  "safeToExecute": false
                }
                Rules:
                - Only use entity keys: title, startDate, startTime, allDay, location, agendaRange.
                - Only use missingFields values from: ["title", "date", "time"].
                - Only use ambiguityMarkers values from: ["time_is_range"].
                - If date and time are explicitly present, missingFields must be [] and ambiguityMarkers must be [].
                - Requests like "Запиши меня к стоматологу на завтра в 14" are calendar events.
                - Short task-like phrases such as "купи молоко", "позвонить маме", "заехать в аптеку" should be treated as calendar/reminder intents rather than UNKNOWN.
                - For that example, title should be "к стоматологу", startDate should be tomorrow, startTime should be "14:00", allDay should be "false".
                - Read-only requests like "Что сегодня", "Планы сегодня", "События завтра" should be LIST_AGENDA with agendaRange "today" or "tomorrow".
                - For LIST_AGENDA, do not return title/startDate/startTime/location unless they are explicitly needed; agendaRange is enough.
                - For LIST_AGENDA with agendaRange set, missingFields must be [] and safeToExecute must be true.
                - For reminder phrasing with a target date inside the task and a separate reminder schedule, use the reminder schedule as startDate and keep the target date in title.
                - Example: "Напомни взять отгул на 29 апреля в следующий понедельник" means title "взять отгул на 29 апреля" and startDate equal to the next Monday, not 29 April.
                - When Conversation status is AWAITING_DATE, short clarification answers like "27.04", "27 апреля", or "27 апреля 2026" mean startDate, not title.
                - Normalize clarification dates to "YYYY-MM-DD".
                - When Conversation status is AWAITING_TIME, short clarification answers like "12", "в 12", or "12:30" mean startTime, not title.
                - Normalize hour-only clarification answers like "12" to "12:00".
                Message: %s
                Conversation status: %s
                Clarification reason: %s
                Pending action entities: %s
                Preferred timezone: %s
                """.formatted(
                request.message().text(),
                request.conversationState().status(),
                clarificationReason(request),
                pendingActionEntities(request),
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

        try {
            return invokeGemini(contentRequest);
        } catch (RuntimeException exception) {
            if (properties.fallbackToStubOnError()) {
                return simulateGeminiResponse(request);
            }
            throw exception;
        }
    }

    private GeminiGenerateContentResponse invokeGemini(GeminiGenerateContentRequest contentRequest) {
        String requestBody = serializeGeminiRequest(contentRequest.prompt());
        HttpRequest request = HttpRequest.newBuilder(buildGeminiUri(contentRequest.model()))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Gemini generateContent failed with status " + response.statusCode());
            }
            return parseGeminiResponse(response.body());
        } catch (IOException exception) {
            throw new IllegalStateException("Gemini response could not be parsed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini request was interrupted", exception);
        }
    }

    private URI buildGeminiUri(String model) {
        String encodedModel = URLEncoder.encode(model, StandardCharsets.UTF_8);
        String encodedApiKey = URLEncoder.encode(properties.apiKey(), StandardCharsets.UTF_8);
        return URI.create(properties.baseUrl() + "/v1beta/models/" + encodedModel + ":generateContent?key=" + encodedApiKey);
    }

    private String serializeGeminiRequest(String prompt) {
        var root = objectMapper.createObjectNode();
        var contents = objectMapper.createArrayNode();
        var content = objectMapper.createObjectNode();
        var parts = objectMapper.createArrayNode();
        parts.add(objectMapper.createObjectNode().put("text", prompt));
        content.set("parts", parts);
        contents.add(content);
        root.set("contents", contents);
        root.set(
                "generationConfig",
                objectMapper.createObjectNode()
                        .put("responseMimeType", "application/json")
                        .put("temperature", 0.1d)
        );
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Gemini request could not be serialized", exception);
        }
    }

    private GeminiGenerateContentResponse parseGeminiResponse(String responseBody) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode textNode = root.at("/candidates/0/content/parts/0/text");
        if (textNode.isMissingNode() || textNode.asText().isBlank()) {
            throw new IllegalStateException("Gemini response did not contain candidate text");
        }

        JsonNode canonicalResponse = objectMapper.readTree(cleanJson(textNode.asText()));
        return new GeminiGenerateContentResponse(
                canonicalResponse.path("intentType").asText("UNKNOWN"),
                readEntities(canonicalResponse.path("entities")),
                clampConfidence(canonicalResponse.path("confidence").asDouble(0.5d)),
                readStringList(canonicalResponse.path("ambiguityMarkers")),
                readStringList(canonicalResponse.path("missingFields")),
                canonicalResponse.path("safeToExecute").asBoolean(false)
        );
    }

    private String cleanJson(String rawText) {
        String cleaned = rawText.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        return cleaned;
    }

    private Map<String, String> readEntities(JsonNode entitiesNode) {
        LinkedHashMap<String, String> entities = new LinkedHashMap<>();
        if (!entitiesNode.isObject()) {
            return entities;
        }

        entitiesNode.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isNull()) {
                entities.put(entry.getKey(), entry.getValue().asText(""));
            }
        });
        return entities;
    }

    private List<String> readStringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }

        ArrayList<String> values = new ArrayList<>();
        node.forEach(item -> {
            String value = item.asText("").trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        });
        return List.copyOf(values);
    }

    private double clampConfidence(double confidence) {
        if (confidence < 0.0d) {
            return 0.0d;
        }
        if (confidence > 1.0d) {
            return 1.0d;
        }
        return confidence;
    }

    private GeminiGenerateContentResponse simulateGeminiResponse(IntentInterpretationRequest request) {
        String originalText = request.message().text().trim();
        String normalizedText = originalText.toLowerCase(Locale.ROOT);

        if (looksLikeAgendaIntent(normalizedText)) {
            return simulateAgendaResponse(normalizedText);
        }

        if (!looksLikeCalendarIntent(normalizedText, request)) {
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

    private GeminiGenerateContentResponse simulateAgendaResponse(String normalizedText) {
        String agendaRange = extractAgendaRange(normalizedText);
        if (agendaRange == null) {
            return new GeminiGenerateContentResponse(
                    "UNKNOWN",
                    Map.of(),
                    0.35d,
                    List.of(),
                    List.of(),
                    false
            );
        }

        return new GeminiGenerateContentResponse(
                "LIST_AGENDA",
                Map.of("agendaRange", agendaRange),
                0.94d,
                List.of(),
                List.of(),
                true
        );
    }

    private IntentInterpretation stabilizeInterpretation(
            IntentInterpretation interpretation,
            IntentInterpretationRequest request
    ) {
        interpretation = stabilizeReminderScheduleInterpretation(interpretation, request);

        IntentInterpretation clarifiedDateInterpretation = stabilizeDateClarificationInterpretation(interpretation, request);
        if (clarifiedDateInterpretation != interpretation) {
            return clarifiedDateInterpretation;
        }

        IntentInterpretation clarifiedTimeInterpretation = stabilizeTimeClarificationInterpretation(interpretation, request);
        if (clarifiedTimeInterpretation != interpretation) {
            return clarifiedTimeInterpretation;
        }

        if (interpretation.intentType() != IntentType.UNKNOWN) {
            return interpretation;
        }

        String originalText = request.message().text().trim();
        String normalizedText = originalText.toLowerCase(Locale.ROOT);
        if (looksLikeAgendaIntent(normalizedText)) {
            String agendaRange = extractAgendaRange(normalizedText);
            if (agendaRange != null) {
                return new IntentInterpretation(
                        new AssistantIntent(IntentType.LIST_AGENDA, Map.of("agendaRange", agendaRange)),
                        new ConfidenceScore(0.82d),
                        List.of(),
                        List.of(),
                        true
                );
            }
        }

        if (!looksLikeImplicitTaskShorthand(originalText)) {
            return interpretation;
        }

        LinkedHashMap<String, String> entities = new LinkedHashMap<>();
        String title = normalizeImplicitTaskTitle(originalText);
        if (!title.isBlank()) {
            entities.put("title", title);
        }

        LocalDate startDate = extractDate(normalizedText, request);
        if (startDate != null) {
            entities.put("startDate", startDate.toString());
        }

        boolean allDay = containsAny(normalizedText, "весь день", "на весь день", "all day");
        if (allDay) {
            entities.put("allDay", "true");
        } else {
            LocalTime startTime = extractTime(normalizedText);
            if (startTime != null) {
                entities.put("startTime", startTime.toString());
            }
            entities.put("allDay", "false");
        }

        ArrayList<String> ambiguityMarkers = new ArrayList<>();
        if (!allDay && !entities.containsKey("startTime")
                && containsAny(normalizedText, "утром", "днем", "днём", "вечером", "morning", "afternoon", "evening")) {
            ambiguityMarkers.add("time_is_range");
        }

        ArrayList<String> missingFields = new ArrayList<>();
        if (entities.getOrDefault("title", "").isBlank()) {
            missingFields.add("title");
        }
        if (entities.getOrDefault("startDate", "").isBlank()) {
            missingFields.add("date");
        }
        if (!Boolean.parseBoolean(entities.getOrDefault("allDay", "false"))
                && entities.getOrDefault("startTime", "").isBlank()
                && ambiguityMarkers.isEmpty()) {
            missingFields.add("time");
        }

        boolean safeToExecute = missingFields.isEmpty() && ambiguityMarkers.isEmpty();
        double confidence = safeToExecute ? 0.78d : 0.58d;
        return new IntentInterpretation(
                new AssistantIntent(IntentType.CREATE_CALENDAR_EVENT, Map.copyOf(entities)),
                new ConfidenceScore(confidence),
                List.copyOf(ambiguityMarkers),
                List.copyOf(missingFields),
                safeToExecute
        );
    }

    private IntentInterpretation stabilizeReminderScheduleInterpretation(
            IntentInterpretation interpretation,
            IntentInterpretationRequest request
    ) {
        String originalText = request.message().text().trim();
        String normalizedText = originalText.toLowerCase(Locale.ROOT);
        if (!containsReminderMarker(normalizedText)) {
            return interpretation;
        }

        LocalDate scheduledReminderDate = extractNextWeekdayDate(normalizedText, request);
        if (scheduledReminderDate == null) {
            return interpretation;
        }

        LinkedHashMap<String, String> entities = new LinkedHashMap<>(interpretation.assistantIntent().entities());
        entities.put("startDate", scheduledReminderDate.toString());

        String reminderTitle = extractReminderTitleWithScheduleRemoved(originalText);
        if (reminderTitle != null && !reminderTitle.isBlank()) {
            entities.put("title", reminderTitle);
        }

        IntentType resolvedIntentType = interpretation.intentType() == IntentType.UNKNOWN
                ? IntentType.CREATE_CALENDAR_EVENT
                : interpretation.intentType();
        List<String> ambiguityMarkers = interpretation.ambiguityMarkers();
        List<String> missingFields = buildMissingFields(resolvedIntentType, entities, ambiguityMarkers);
        boolean safeToExecute = resolvedIntentType != IntentType.UNKNOWN
                && missingFields.isEmpty()
                && ambiguityMarkers.isEmpty();

        return new IntentInterpretation(
                new AssistantIntent(resolvedIntentType, Map.copyOf(entities)),
                new ConfidenceScore(Math.max(interpretation.confidenceScore().value(), 0.78d)),
                ambiguityMarkers,
                missingFields,
                safeToExecute
        );
    }

    private IntentInterpretation stabilizeDateClarificationInterpretation(
            IntentInterpretation interpretation,
            IntentInterpretationRequest request
    ) {
        if (!isAwaitingDateClarification(request)) {
            return interpretation;
        }
        if (!interpretation.assistantIntent().entities().getOrDefault("startDate", "").isBlank()) {
            return interpretation;
        }

        LocalDate clarifiedDate = extractDate(request.message().text().trim().toLowerCase(Locale.ROOT), request);
        if (clarifiedDate == null) {
            return interpretation;
        }

        LinkedHashMap<String, String> entities = new LinkedHashMap<>(interpretation.assistantIntent().entities());
        entities.remove("title");
        entities.put("startDate", clarifiedDate.toString());

        ArrayList<String> ambiguityMarkers = new ArrayList<>(interpretation.ambiguityMarkers());
        IntentType resolvedIntentType = interpretation.intentType() == IntentType.UNKNOWN
                ? request.conversationState().pendingAction().interpretation().intentType()
                : interpretation.intentType();
        List<String> missingFields = buildMissingFields(resolvedIntentType, entities, ambiguityMarkers);
        boolean safeToExecute = resolvedIntentType != IntentType.UNKNOWN
                && missingFields.isEmpty()
                && ambiguityMarkers.isEmpty();

        return new IntentInterpretation(
                new AssistantIntent(resolvedIntentType, Map.copyOf(entities)),
                new ConfidenceScore(Math.max(interpretation.confidenceScore().value(), 0.76d)),
                List.copyOf(ambiguityMarkers),
                missingFields,
                safeToExecute
        );
    }

    private IntentInterpretation stabilizeTimeClarificationInterpretation(
            IntentInterpretation interpretation,
            IntentInterpretationRequest request
    ) {
        if (!isAwaitingTimeClarification(request)) {
            return interpretation;
        }
        if (!interpretation.assistantIntent().entities().getOrDefault("startTime", "").isBlank()) {
            return interpretation;
        }

        LocalTime clarifiedTime = extractClarificationTimeReply(request.message().text());
        if (clarifiedTime == null) {
            return interpretation;
        }

        LinkedHashMap<String, String> entities = new LinkedHashMap<>(interpretation.assistantIntent().entities());
        entities.remove("title");
        entities.put("startTime", clarifiedTime.toString());
        entities.put("allDay", "false");

        ArrayList<String> ambiguityMarkers = new ArrayList<>(interpretation.ambiguityMarkers());
        ambiguityMarkers.removeIf("time_is_range"::equals);

        IntentType resolvedIntentType = interpretation.intentType() == IntentType.UNKNOWN
                ? request.conversationState().pendingAction().interpretation().intentType()
                : interpretation.intentType();
        List<String> missingFields = buildMissingFields(resolvedIntentType, entities, ambiguityMarkers);
        boolean safeToExecute = resolvedIntentType != IntentType.UNKNOWN
                && missingFields.isEmpty()
                && ambiguityMarkers.isEmpty();

        return new IntentInterpretation(
                new AssistantIntent(resolvedIntentType, Map.copyOf(entities)),
                new ConfidenceScore(Math.max(interpretation.confidenceScore().value(), 0.76d)),
                List.copyOf(ambiguityMarkers),
                missingFields,
                safeToExecute
        );
    }

    private boolean isAwaitingDateClarification(IntentInterpretationRequest request) {
        if (!request.conversationState().isAwaitingClarification()
                || request.conversationState().clarificationRequest() == null) {
            return false;
        }

        String reason = request.conversationState().clarificationRequest().reason();
        return "date".equals(reason)
                || request.conversationState().clarificationRequest().missingFields().contains("date");
    }

    private boolean isAwaitingTimeClarification(IntentInterpretationRequest request) {
        if (!request.conversationState().isAwaitingClarification()
                || request.conversationState().clarificationRequest() == null) {
            return false;
        }

        String reason = request.conversationState().clarificationRequest().reason();
        return "time".equals(reason)
                || "time_is_range".equals(reason)
                || request.conversationState().clarificationRequest().missingFields().contains("time")
                || request.conversationState().clarificationRequest().missingFields().contains("time_is_range");
    }

    private boolean containsReminderMarker(String normalizedText) {
        return containsAny(normalizedText, "напомни", "remind");
    }

    private String extractReminderTitleWithScheduleRemoved(String originalText) {
        String cleaned = originalText;
        cleaned = stripPhraseIgnoreCase(cleaned, "напомни");
        cleaned = stripPhraseIgnoreCase(cleaned, "remind");
        cleaned = cleaned
                .replaceAll(NEXT_WEEKDAY_REGEX, " ")
                .replaceAll("(?iu)\\b(сегодня|завтра|today|tomorrow)\\b", " ")
                .replaceAll("(?iu)(?:в|at)\\s*\\d{1,2}(?::\\d{2})?(?:\\s*(утра|дня|вечера|ночи|am|pm))?", " ")
                .replaceAll("(?iu)(?:в|at)\\s+(офисе|zoom|online|онлайн)", " ")
                .replaceAll("\\s+", " ")
                .trim();
        cleaned = cleaned.replaceAll("(?iu)^меня\\s+", "");
        while (cleaned.matches("(?iu).*(?:\\s+(?:в|at|to|for))$")) {
            cleaned = cleaned.replaceAll("(?iu)\\s+(?:в|at|to|for)$", "").trim();
        }
        return cleaned.isBlank() ? null : cleaned;
    }

    private LocalTime extractClarificationTimeReply(String originalText) {
        Matcher matcher = CLARIFICATION_TIME_REPLY_PATTERN.matcher(originalText == null ? "" : originalText.trim());
        if (!matcher.matches()) {
            return null;
        }

        int hour = Integer.parseInt(matcher.group(1));
        int minute = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        return toLocalTime(hour, minute, matcher.group(3));
    }

    private List<String> buildMissingFields(
            IntentType intentType,
            Map<String, String> entities,
            List<String> ambiguityMarkers
    ) {
        ArrayList<String> missingFields = new ArrayList<>();
        if (intentType == IntentType.LIST_AGENDA) {
            if (entities.getOrDefault("agendaRange", "").isBlank()) {
                missingFields.add("date");
            }
            return List.copyOf(missingFields);
        }

        if (entities.getOrDefault("title", "").isBlank()) {
            missingFields.add("title");
        }
        if (entities.getOrDefault("startDate", "").isBlank()) {
            missingFields.add("date");
        }
        if (!Boolean.parseBoolean(entities.getOrDefault("allDay", "false"))
                && entities.getOrDefault("startTime", "").isBlank()
                && ambiguityMarkers.isEmpty()) {
            missingFields.add("time");
        }
        return List.copyOf(missingFields);
    }

    private String clarificationReason(IntentInterpretationRequest request) {
        if (request.conversationState().clarificationRequest() == null
                || request.conversationState().clarificationRequest().reason() == null
                || request.conversationState().clarificationRequest().reason().isBlank()) {
            return "none";
        }
        return request.conversationState().clarificationRequest().reason();
    }

    private String pendingActionEntities(IntentInterpretationRequest request) {
        if (request.conversationState().pendingAction() == null) {
            return "{}";
        }
        return request.conversationState().pendingAction().interpretation().assistantIntent().entities().toString();
    }

    private boolean looksLikeCalendarIntent(String normalizedText, IntentInterpretationRequest request) {
        if (request.conversationState().isAwaitingClarification()) {
            return true;
        }
        if (containsCreateMarker(normalizedText)
                || normalizedText.contains("записаться")) {
            return true;
        }

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

    private boolean looksLikeAgendaIntent(String normalizedText) {
        if (containsCreateMarker(normalizedText)) {
            return false;
        }

        String agendaRange = extractAgendaRange(normalizedText);
        if (agendaRange == null) {
            return false;
        }

        return containsAny(
                normalizedText,
                "что",
                "план",
                "событ",
                "расписан",
                "agenda",
                "plans",
                "schedule",
                "events",
                "what"
        );
    }

    private boolean containsCreateMarker(String normalizedText) {
        return containsAny(
                normalizedText,
                "запиши",
                "запишите",
                "напомни",
                "создай",
                "добавь",
                "запланиру",
                "remind",
                "create",
                "add",
                "schedule"
        );
    }

    private String extractAgendaRange(String normalizedText) {
        if (containsAny(normalizedText, "завтра", "tomorrow")) {
            return "tomorrow";
        }
        if (containsAny(normalizedText, "сегодня", "today")) {
            return "today";
        }
        return null;
    }

    private boolean looksLikeImplicitTaskShorthand(String originalText) {
        String normalizedText = originalText.trim().toLowerCase(Locale.ROOT);
        if (normalizedText.isBlank() || normalizedText.endsWith("?")) {
            return false;
        }
        if (normalizedText.contains("http://") || normalizedText.contains("https://") || normalizedText.contains("www.")) {
            return false;
        }
        if (containsAny(
                normalizedText,
                "привет", "здравств", "доброе утро", "добрый день", "добрый вечер",
                "спасибо", "пока", "как дела", "hello", "hi", "thanks", "bye"
        )) {
            return false;
        }

        String[] words = normalizedText.split("\\s+");
        if (words.length == 0 || words.length > 6) {
            return false;
        }

        String firstWord = words[0];
        return firstWord.matches("(?iu).*(ть|ти|ться|ись|й|и)$") || words.length <= 3;
    }

    private String extractImplicitTaskTitle(String originalText) {
        String cleaned = originalText
                .replaceAll("(?iu)(?:в|at)\\s*\\d{1,2}(?::\\d{2})?(?:\\s*(утра|дня|вечера|ночи|am|pm))?", " ")
                .replaceAll("\\d{1,2}\\.\\d{1,2}(?:\\.\\d{4})?", " ")
                .replaceAll(WORD_MONTH_DATE_REGEX, " ")
                .replaceAll("\\d{4}-\\d{2}-\\d{2}", " ")
                .replaceAll("(?iu)\\b(сегодня|завтра|today|tomorrow|весь день|all day|утром|днем|днём|вечером|morning|afternoon|evening)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        while (cleaned.matches("(?iu).*(?:\\s+(?:на|в|at|to|for))$")) {
            cleaned = cleaned.replaceAll("(?iu)\\s+(?:на|в|at|to|for)$", "").trim();
        }
        return cleaned;
    }

    private String normalizeImplicitTaskTitle(String originalText) {
        String cleaned = extractImplicitTaskTitle(originalText);
        for (String suffix : List.of(
                " сегодня", " завтра",
                " today", " tomorrow",
                " весь день", " на весь день", " all day",
                " утром", " днем", " днём", " вечером",
                " morning", " afternoon", " evening"
        )) {
            if (cleaned.regionMatches(true, Math.max(cleaned.length() - suffix.length(), 0), suffix, 0, suffix.length())) {
                cleaned = cleaned.substring(0, cleaned.length() - suffix.length()).trim();
            }
        }
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    private String extractTitle(String originalText) {
        String cleaned = originalText;
        cleaned = stripPhraseIgnoreCase(cleaned, "запиши меня");
        cleaned = stripPhraseIgnoreCase(cleaned, "запишите меня");
        cleaned = stripPhraseIgnoreCase(cleaned, "запиши");
        cleaned = stripPhraseIgnoreCase(cleaned, "запишите");
        cleaned = stripPhraseIgnoreCase(cleaned, "записаться");
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
                .replaceAll("(?iu)(?:в|at)\\s*\\d{1,2}(?::\\d{2})?(?:\\s*(утра|дня|вечера|ночи|am|pm))?", " ")
                .replaceAll("\\d{1,2}\\.\\d{1,2}(?:\\.\\d{4})?", " ")
                .replaceAll(WORD_MONTH_DATE_REGEX, " ")
                .replaceAll("\\d{4}-\\d{2}-\\d{2}", " ")
                .replaceAll("(?iu)(?:в|at)\\s+(офисе|zoom|online|онлайн)", " ")
                .replaceAll("\\s+", " ")
                .trim();
        cleaned = cleaned.replaceAll("(?iu)^меня\\s+", "");
        while (cleaned.matches("(?iu).*(?:\\s+(?:на|в|at|to|for))$")) {
            cleaned = cleaned.replaceAll("(?iu)\\s+(?:на|в|at|to|for)$", "").trim();
        }

        return cleaned.isBlank() ? null : cleaned;
    }

    private LocalDate extractDate(String normalizedText, IntentInterpretationRequest request) {
        LocalDate today = currentLocalDate(request);
        if (containsAny(normalizedText, "завтра", "tomorrow")) {
            return today.plusDays(1);
        }
        if (containsAny(normalizedText, "сегодня", "today")) {
            return today;
        }
        LocalDate nextWeekdayDate = extractNextWeekdayDate(normalizedText, request);
        if (nextWeekdayDate != null) {
            return nextWeekdayDate;
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
            try {
                return LocalDate.of(year, month, day);
            } catch (RuntimeException exception) {
                return null;
            }
        }

        Matcher wordMonthMatcher = WORD_MONTH_DATE_PATTERN.matcher(normalizedText);
        if (wordMonthMatcher.find()) {
            int day = Integer.parseInt(wordMonthMatcher.group(1));
            Integer month = RUSSIAN_MONTH_NUMBERS.get(wordMonthMatcher.group(2).toLowerCase(Locale.ROOT));
            int year = wordMonthMatcher.group(3) == null ? today.getYear() : Integer.parseInt(wordMonthMatcher.group(3));
            if (month == null) {
                return null;
            }
            try {
                return LocalDate.of(year, month, day);
            } catch (RuntimeException exception) {
                return null;
            }
        }

        return null;
    }

    private LocalDate extractNextWeekdayDate(String normalizedText, IntentInterpretationRequest request) {
        Matcher nextWeekdayMatcher = NEXT_WEEKDAY_PATTERN.matcher(normalizedText);
        if (!nextWeekdayMatcher.find()) {
            return null;
        }

        DayOfWeek targetDay = WEEKDAY_ALIASES.get(nextWeekdayMatcher.group(1).toLowerCase(Locale.ROOT));
        if (targetDay == null) {
            return null;
        }

        return resolveNextWeekdayDate(currentLocalDate(request), targetDay);
    }

    private LocalDate resolveNextWeekdayDate(LocalDate referenceDate, DayOfWeek targetDay) {
        int daysUntil = targetDay.getValue() - referenceDate.getDayOfWeek().getValue();
        if (daysUntil <= 0) {
            daysUntil += 7;
        }
        return referenceDate.plusDays(daysUntil);
    }

    private LocalDate currentLocalDate(IntentInterpretationRequest request) {
        return request.message()
                .receivedAt()
                .atZone(request.userContext().preferredTimezone().toZoneId())
                .toLocalDate();
    }

    private LocalTime extractTime(String normalizedText) {
        Matcher exactTimeMatcher = TIME_PATTERN.matcher(normalizedText);
        if (exactTimeMatcher.find()) {
            int hour = Integer.parseInt(exactTimeMatcher.group(1));
            int minute = Integer.parseInt(exactTimeMatcher.group(2));
            return toLocalTime(hour, minute, exactTimeMatcher.group(3));
        }

        Matcher hourOnlyMatcher = HOUR_ONLY_PATTERN.matcher(normalizedText);
        if (hourOnlyMatcher.find()) {
            int hour = Integer.parseInt(hourOnlyMatcher.group(1));
            return toLocalTime(hour, 0, hourOnlyMatcher.group(2));
        }

        return null;
    }

    private LocalTime toLocalTime(int hour, int minute, String meridiem) {
        int normalizedHour = normalizeHour(hour, meridiem);
        if (normalizedHour < 0 || normalizedHour > 23 || minute < 0 || minute > 59) {
            return null;
        }
        return LocalTime.of(normalizedHour, minute);
    }

    private int normalizeHour(int hour, String meridiem) {
        String normalizedMeridiem = meridiem == null ? "" : meridiem.trim().toLowerCase(Locale.ROOT);
        return switch (normalizedMeridiem) {
            case "am", "утра" -> hour == 12 ? 0 : hour;
            case "pm", "дня", "вечера" -> hour < 12 ? hour + 12 : hour;
            case "ночи" -> {
                if (hour == 12) {
                    yield 0;
                }
                yield hour <= 5 ? hour : hour + 12;
            }
            default -> hour;
        };
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
