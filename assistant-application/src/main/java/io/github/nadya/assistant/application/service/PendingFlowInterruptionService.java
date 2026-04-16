package io.github.nadya.assistant.application.service;

import io.github.nadya.assistant.domain.conversation.IncomingUserMessage;
import io.github.nadya.assistant.domain.conversation.PendingAction;
import io.github.nadya.assistant.domain.intent.IntentInterpretation;

import java.util.List;
import java.util.Locale;

public final class PendingFlowInterruptionService {

    private static final List<String> REQUEST_MARKERS = List.of(
            "создай",
            "добавь",
            "напомни",
            "запланируй",
            "create",
            "add",
            "remind",
            "schedule"
    );

    public boolean isUnrelatedNewRequest(
            IncomingUserMessage message,
            PendingAction pendingAction,
            IntentInterpretation followUpInterpretation
    ) {
        String normalizedText = normalize(message.text());
        if (!containsRequestMarker(normalizedText)) {
            return false;
        }

        String followUpTitle = normalize(followUpInterpretation.assistantIntent().entities().get("title"));
        String currentTitle = normalize(pendingAction.interpretation().assistantIntent().entities().get("title"));

        if (followUpTitle.isBlank()) {
            return true;
        }
        if (currentTitle.isBlank()) {
            return true;
        }

        return !followUpTitle.equals(currentTitle);
    }

    private boolean containsRequestMarker(String normalizedText) {
        for (String marker : REQUEST_MARKERS) {
            if (normalizedText.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
