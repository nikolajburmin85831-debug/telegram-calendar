package io.github.nadya.assistant.application.service;

import io.github.nadya.assistant.domain.common.ChannelType;
import io.github.nadya.assistant.domain.common.ConfidenceScore;
import io.github.nadya.assistant.domain.conversation.ClarificationRequest;
import io.github.nadya.assistant.domain.conversation.IncomingUserMessage;
import io.github.nadya.assistant.domain.conversation.PendingAction;
import io.github.nadya.assistant.domain.intent.AssistantIntent;
import io.github.nadya.assistant.domain.intent.IntentInterpretation;
import io.github.nadya.assistant.domain.intent.IntentType;
import io.github.nadya.assistant.domain.user.UserIdentity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingActionMergeServiceTest {

    private final PendingActionMergeService mergeService = new PendingActionMergeService();

    @Test
    void shouldMergeDateAndHourFromSingleFollowUp() {
        PendingAction pendingAction = pendingAction(
                entities("title", "встреча с командой"),
                List.of("date", "time"),
                List.of()
        );

        IntentInterpretation merged = mergeService.merge(
                pendingAction,
                new ClarificationRequest("date", List.of("date", "time"), "Уточните дату и время", pendingAction.pendingActionId()),
                interpretation(
                        entities(
                                "startDate", "2026-04-17",
                                "startTime", "10:00",
                                "allDay", "false"
                        ),
                        List.of("title"),
                        List.of(),
                        false,
                        0.92d
                )
        );

        assertEquals("встреча с командой", merged.assistantIntent().entities().get("title"));
        assertEquals("2026-04-17", merged.assistantIntent().entities().get("startDate"));
        assertEquals("10:00", merged.assistantIntent().entities().get("startTime"));
        assertEquals("false", merged.assistantIntent().entities().get("allDay"));
        assertTrue(merged.missingFields().isEmpty());
        assertTrue(merged.ambiguityMarkers().isEmpty());
        assertTrue(merged.safeToExecute());
    }

    @Test
    void shouldTreatAllDayFollowUpAsResolvedTiming() {
        PendingAction pendingAction = pendingAction(
                entities(
                        "title", "оплатить аренду",
                        "startDate", "2026-04-17",
                        "allDay", "false"
                ),
                List.of("time"),
                List.of()
        );

        IntentInterpretation merged = mergeService.merge(
                pendingAction,
                new ClarificationRequest("time", List.of("time"), "Во сколько должно начаться событие?", pendingAction.pendingActionId()),
                interpretation(
                        entities("allDay", "true"),
                        List.of("title", "date"),
                        List.of(),
                        false,
                        0.85d
                )
        );

        assertEquals("оплатить аренду", merged.assistantIntent().entities().get("title"));
        assertEquals("2026-04-17", merged.assistantIntent().entities().get("startDate"));
        assertEquals("true", merged.assistantIntent().entities().get("allDay"));
        assertNull(merged.assistantIntent().entities().get("startTime"));
        assertTrue(merged.missingFields().isEmpty());
        assertTrue(merged.safeToExecute());
        assertFalse(merged.assistantIntent().entities().containsKey("startTime"));
    }

    @Test
    void shouldNotOverwriteKnownTitleWhenClarifyingTime() {
        PendingAction pendingAction = pendingAction(
                entities(
                        "title", "РєСѓРїРё РјРѕР»РѕРєРѕ",
                        "startDate", "2026-04-17",
                        "allDay", "false"
                ),
                List.of("time"),
                List.of()
        );

        IntentInterpretation merged = mergeService.merge(
                pendingAction,
                new ClarificationRequest("time", List.of("time"), "Р’Рѕ СЃРєРѕР»СЊРєРѕ РґРѕР»Р¶РЅРѕ РЅР°С‡Р°С‚СЊСЃСЏ СЃРѕР±С‹С‚РёРµ?", pendingAction.pendingActionId()),
                interpretation(
                        entities(
                                "title", "19:00",
                                "startTime", "19:00",
                                "allDay", "false"
                        ),
                        List.of("date"),
                        List.of(),
                        false,
                        0.88d
                )
        );

        assertEquals("РєСѓРїРё РјРѕР»РѕРєРѕ", merged.assistantIntent().entities().get("title"));
        assertEquals("2026-04-17", merged.assistantIntent().entities().get("startDate"));
        assertEquals("19:00", merged.assistantIntent().entities().get("startTime"));
        assertEquals("false", merged.assistantIntent().entities().get("allDay"));
        assertTrue(merged.missingFields().isEmpty());
        assertTrue(merged.safeToExecute());
    }

    private PendingAction pendingAction(
            LinkedHashMap<String, String> entities,
            List<String> missingFields,
            List<String> ambiguityMarkers
    ) {
        return new PendingAction(
                "pending-1",
                new IncomingUserMessage(
                        "internal-1",
                        "external-1",
                        new UserIdentity("telegram-user:42"),
                        ChannelType.TELEGRAM,
                        "telegram-chat:101",
                        "Создай встречу",
                        Instant.parse("2026-04-16T20:15:30Z")
                ),
                interpretation(entities, missingFields, ambiguityMarkers, false, 0.70d),
                Instant.parse("2026-04-16T20:15:30Z")
        );
    }

    private IntentInterpretation interpretation(
            LinkedHashMap<String, String> entities,
            List<String> missingFields,
            List<String> ambiguityMarkers,
            boolean safeToExecute,
            double confidence
    ) {
        return new IntentInterpretation(
                new AssistantIntent(IntentType.CREATE_CALENDAR_EVENT, entities),
                new ConfidenceScore(confidence),
                ambiguityMarkers,
                missingFields,
                safeToExecute
        );
    }

    private LinkedHashMap<String, String> entities(String... pairs) {
        LinkedHashMap<String, String> entities = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            entities.put(pairs[index], pairs[index + 1]);
        }
        return entities;
    }
}
