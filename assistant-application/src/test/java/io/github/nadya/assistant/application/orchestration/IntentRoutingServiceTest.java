package io.github.nadya.assistant.application.orchestration;

import io.github.nadya.assistant.application.service.ConfirmationPolicyService;
import io.github.nadya.assistant.domain.common.ChannelType;
import io.github.nadya.assistant.domain.common.ConfidenceScore;
import io.github.nadya.assistant.domain.conversation.IncomingUserMessage;
import io.github.nadya.assistant.domain.intent.AssistantIntent;
import io.github.nadya.assistant.domain.intent.IntentInterpretation;
import io.github.nadya.assistant.domain.intent.IntentType;
import io.github.nadya.assistant.domain.policy.ExecutionOutcome;
import io.github.nadya.assistant.domain.user.ConfirmationPreference;
import io.github.nadya.assistant.domain.user.UserContext;
import io.github.nadya.assistant.domain.user.UserIdentity;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntentRoutingServiceTest {

    private final IntentRoutingService service = new IntentRoutingService(new ConfirmationPolicyService());

    @Test
    void shouldRejectUnknownIntent() {
        var decision = service.decide(message(), userContext(ConfirmationPreference.AUTO_EXECUTE), interpretation(IntentType.UNKNOWN, Map.of(), List.of(), List.of(), false, 0.2d));

        assertEquals(ExecutionOutcome.REJECT, decision.outcome());
    }

    @Test
    void shouldAskForClarificationWhenDateIsMissing() {
        var decision = service.decide(
                message(),
                userContext(ConfirmationPreference.AUTO_EXECUTE),
                interpretation(
                        IntentType.CREATE_CALENDAR_EVENT,
                        Map.of("title", "встреча с командой"),
                        List.of(),
                        List.of("date"),
                        false,
                        0.7d
                )
        );

        assertEquals(ExecutionOutcome.ASK_FOR_CLARIFICATION, decision.outcome());
        assertEquals("date", decision.clarificationRequest().reason());
        assertEquals("На какую дату создать событие?", decision.clarificationRequest().userFacingQuestion());
    }

    @Test
    void shouldAskForConfirmationWhenUserPreferenceRequiresIt() {
        var decision = service.decide(
                message(),
                userContext(ConfirmationPreference.REQUIRE_CONFIRMATION),
                interpretation(
                        IntentType.CREATE_CALENDAR_EVENT,
                        Map.of(
                                "title", "демо",
                                "startDate", "2026-04-17",
                                "startTime", "09:00",
                                "allDay", "false"
                        ),
                        List.of(),
                        List.of(),
                        true,
                        0.95d
                )
        );

        assertEquals(ExecutionOutcome.ASK_FOR_CONFIRMATION, decision.outcome());
        assertEquals("Создать событие \"демо\" на 2026-04-17 в 09:00", decision.pendingConfirmation().actionSummary());
    }

    private IncomingUserMessage message() {
        return new IncomingUserMessage(
                "internal-1",
                "external-1",
                new UserIdentity("telegram-user:42"),
                ChannelType.TELEGRAM,
                "telegram-chat:101",
                "Создай событие",
                Instant.parse("2026-04-16T20:15:30Z")
        );
    }

    private UserContext userContext(ConfirmationPreference preference) {
        return new UserContext(
                new UserIdentity("telegram-user:42"),
                new io.github.nadya.assistant.domain.common.Timezone("Europe/Moscow"),
                "ru",
                preference,
                Duration.ofHours(1),
                "telegram-chat:101"
        );
    }

    private IntentInterpretation interpretation(
            IntentType intentType,
            Map<String, String> entities,
            List<String> ambiguityMarkers,
            List<String> missingFields,
            boolean safeToExecute,
            double confidence
    ) {
        return new IntentInterpretation(
                new AssistantIntent(intentType, entities),
                new ConfidenceScore(confidence),
                ambiguityMarkers,
                missingFields,
                safeToExecute
        );
    }
}
