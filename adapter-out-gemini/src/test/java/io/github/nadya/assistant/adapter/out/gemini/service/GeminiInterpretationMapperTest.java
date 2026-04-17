package io.github.nadya.assistant.adapter.out.gemini.service;

import io.github.nadya.assistant.adapter.out.gemini.dto.GeminiGenerateContentResponse;
import io.github.nadya.assistant.adapter.out.gemini.mapper.GeminiInterpretationMapper;
import io.github.nadya.assistant.domain.intent.IntentType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiInterpretationMapperTest {

    private final GeminiInterpretationMapper mapper = new GeminiInterpretationMapper();

    @Test
    void shouldNormalizeAlternativeEntityKeysAndIgnoreUnsupportedMissingFields() {
        GeminiGenerateContentResponse response = new GeminiGenerateContentResponse(
                "create_calendar_event",
                Map.of(
                        "summary", "к стоматологу",
                        "date", "2026-04-17",
                        "time", "14"
                ),
                0.88d,
                List.of(),
                List.of("details"),
                false
        );

        var interpretation = mapper.map(response);

        assertEquals(IntentType.CREATE_CALENDAR_EVENT, interpretation.intentType());
        assertEquals("к стоматологу", interpretation.assistantIntent().entities().get("title"));
        assertEquals("2026-04-17", interpretation.assistantIntent().entities().get("startDate"));
        assertEquals("14:00", interpretation.assistantIntent().entities().get("startTime"));
        assertEquals("false", interpretation.assistantIntent().entities().get("allDay"));
        assertTrue(interpretation.missingFields().isEmpty());
        assertTrue(interpretation.ambiguityMarkers().isEmpty());
        assertTrue(interpretation.safeToExecute());
    }

    @Test
    void shouldCanonicalizeUnexpectedGapNamesFromGemini() {
        GeminiGenerateContentResponse response = new GeminiGenerateContentResponse(
                "CREATE_CALENDAR_EVENT",
                Map.of("title", "к стоматологу"),
                0.61d,
                List.of("vague_time"),
                List.of("startDate"),
                false
        );

        var interpretation = mapper.map(response);

        assertEquals(IntentType.CREATE_CALENDAR_EVENT, interpretation.intentType());
        assertEquals(List.of("time_is_range"), interpretation.ambiguityMarkers());
        assertEquals(List.of("date"), interpretation.missingFields());
    }
}
