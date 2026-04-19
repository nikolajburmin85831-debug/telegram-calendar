package io.github.nadya.assistant.application.orchestration;

import io.github.nadya.assistant.application.service.ConfirmationPolicyService;
import io.github.nadya.assistant.domain.conversation.ClarificationRequest;
import io.github.nadya.assistant.domain.conversation.IncomingUserMessage;
import io.github.nadya.assistant.domain.conversation.PendingConfirmation;
import io.github.nadya.assistant.domain.intent.IntentInterpretation;
import io.github.nadya.assistant.domain.intent.IntentType;
import io.github.nadya.assistant.domain.policy.ExecutionDecision;
import io.github.nadya.assistant.domain.user.UserContext;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

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
            return ExecutionDecision.reject("Пока я умею создавать события и показывать планы на сегодня или завтра.");
        }

        if (interpretation.intentType() == IntentType.LIST_AGENDA) {
            if (requiresClarification(interpretation)) {
                return ExecutionDecision.reject("Пока я могу показать планы только на сегодня или на завтра.");
            }
            return ExecutionDecision.executeNow();
        }

        if (requiresClarification(interpretation)) {
            return ExecutionDecision.askForClarification(buildClarificationRequest(message, interpretation));
        }

        if (confirmationPolicyService.requiresConfirmation(userContext, interpretation)) {
            return ExecutionDecision.askForConfirmation(buildPendingConfirmation(message, interpretation));
        }

        return ExecutionDecision.executeNow();
    }

    private boolean requiresClarification(IntentInterpretation interpretation) {
        return !interpretation.missingFields().isEmpty()
                || !interpretation.ambiguityMarkers().isEmpty()
                || !interpretation.safeToExecute();
    }

    private ClarificationRequest buildClarificationRequest(
            IncomingUserMessage message,
            IntentInterpretation interpretation
    ) {
        List<String> problems = !interpretation.missingFields().isEmpty()
                ? interpretation.missingFields()
                : interpretation.ambiguityMarkers();
        String primaryGap = problems.get(0);

        String question = switch (primaryGap) {
            case "date" -> "На какую дату создать событие?";
            case "time", "time_is_range" -> "Во сколько должно начаться событие?";
            case "title" -> "Как назвать событие?";
            default -> "Уточните, пожалуйста, детали события.";
        };

        return new ClarificationRequest(
                primaryGap,
                problems,
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
        String timing = Boolean.parseBoolean(interpretation.assistantIntent().entities().getOrDefault("allDay", "false"))
                ? "на весь день"
                : "в " + interpretation.assistantIntent().entities().getOrDefault("startTime", "неизвестное время");
        String summary = "Создать событие \"%s\" на %s %s".formatted(title, date, timing);

        return new PendingConfirmation(
                "pending-" + message.internalMessageId(),
                message.userId(),
                summary,
                summary + "? Ответьте \"да\" или \"нет\".",
                Instant.now().plus(30, ChronoUnit.MINUTES),
                message.internalMessageId()
        );
    }
}
