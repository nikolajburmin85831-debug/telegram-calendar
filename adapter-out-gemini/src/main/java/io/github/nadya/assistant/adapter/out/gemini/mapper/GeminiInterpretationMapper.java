package io.github.nadya.assistant.adapter.out.gemini.mapper;

import io.github.nadya.assistant.adapter.out.gemini.dto.GeminiGenerateContentResponse;
import io.github.nadya.assistant.domain.common.ConfidenceScore;
import io.github.nadya.assistant.domain.intent.AssistantIntent;
import io.github.nadya.assistant.domain.intent.IntentInterpretation;
import io.github.nadya.assistant.domain.intent.IntentType;

public final class GeminiInterpretationMapper {

    public IntentInterpretation map(GeminiGenerateContentResponse response) {
        IntentType intentType = switch (response.intentType()) {
            case "CREATE_CALENDAR_EVENT" -> IntentType.CREATE_CALENDAR_EVENT;
            default -> IntentType.UNKNOWN;
        };

        return new IntentInterpretation(
                new AssistantIntent(intentType, response.entities()),
                new ConfidenceScore(response.confidence()),
                response.ambiguityMarkers(),
                response.missingFields(),
                response.safeToExecute()
        );
    }
}
