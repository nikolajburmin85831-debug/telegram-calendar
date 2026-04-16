package io.github.nadya.assistant.application.orchestration;

import io.github.nadya.assistant.domain.conversation.ClarificationRequest;
import io.github.nadya.assistant.domain.conversation.IncomingUserMessage;
import io.github.nadya.assistant.domain.conversation.PendingConfirmation;
import io.github.nadya.assistant.domain.intent.IntentInterpretation;
import io.github.nadya.assistant.domain.intent.IntentType;
import io.github.nadya.assistant.domain.policy.ExecutionDecision;
import io.github.nadya.assistant.domain.user.UserContext;
import io.github.nadya.assistant.application.service.ConfirmationPolicyService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class IntentRoutingService {

    private final ConfirmationPolicyService confirmationPolicyService;

    public IntentRoutingService(ConfirmationPolicyService confirmationPolicyService) {
        this.confirmationPolicyService = confirmationPolicyService;
    }

    public ExecutionDecision decide(
            IncomingUserMessage message,
            UserContext userContext,
            IntentInterpretation interpretation
    ) {
        if (interpretation.intentType() == IntentType.UNKNOWN) {
            return ExecutionDecision.reject("Пока поддерживается только создание событий календаря.");
        }

        if (!interpretation.missingFields().isEmpty() || !interpretation.ambiguityMarkers().isEmpty() || !interpretation.safeToExecute()) {
            return ExecutionDecision.askForClarification(buildClarificationRequest(message, interpretation));
        }

        if (confirmationPolicyService.requiresConfirmation(userContext, interpretation)) {
            return ExecutionDecision.askForConfirmation(buildPendingConfirmation(message, interpretation));
        }

        return ExecutionDecision.executeNow();
    }

    private ClarificationRequest buildClarificationRequest(
            IncomingUserMessage message,
            IntentInterpretation interpretation
    ) {
        String primaryGap = !interpretation.missingFields().isEmpty()
                ? interpretation.missingFields().get(0)
                : interpretation.ambiguityMarkers().get(0);

        String question = switch (primaryGap) {
            case "date" -> "На какую дату создать событие?";
            case "time", "time_is_range" -> "Во сколько должно начаться событие?";
            case "title" -> "Как назвать событие?";
            default -> "Уточните, пожалуйста, детали события.";
        };

        return new ClarificationRequest(
                "missing_or_ambiguous_data",
                interpretation.missingFields().isEmpty()
                        ? interpretation.ambiguityMarkers()
                        : interpretation.missingFields(),
                question,
                message.internalMessageId()
        );
    }

    private PendingConfirmation buildPendingConfirmation(
            IncomingUserMessage message,
            IntentInterpretation interpretation
    ) {
        String title = interpretation.assistantIntent().entities().getOrDefault("title", "новое событие");
        String date = interpretation.assistantIntent().entities().getOrDefault("startDate", "без даты");
        String time = interpretation.assistantIntent().entities().getOrDefault("startTime", "без времени");
        String summary = "Создать событие \"%s\" на %s %s".formatted(title, date, time);

        return new PendingConfirmation(
                "pending-" + message.internalMessageId(),
                message.userId(),
                summary,
                summary + "? Ответьте 'да' или 'нет'.",
                Instant.now().plus(30, ChronoUnit.MINUTES),
                message.internalMessageId()
        );
    }
}
