package io.github.nadya.assistant.application.service;

import java.util.Locale;
import java.util.Set;

public final class ConversationControlService {

    private static final Set<String> POSITIVE_CONFIRMATIONS = Set.of(
            "да",
            "yes",
            "ok",
            "ок",
            "ага",
            "подтверждаю"
    );

    private static final Set<String> NEGATIVE_CONFIRMATIONS = Set.of(
            "нет",
            "no",
            "неа"
    );

    private static final Set<String> CANCEL_COMMANDS = Set.of(
            "отмена",
            "отменить",
            "cancel",
            "стоп"
    );

    public ConfirmationReply resolveConfirmationReply(String input) {
        String normalized = normalize(input);
        if (POSITIVE_CONFIRMATIONS.contains(normalized)) {
            return ConfirmationReply.APPROVE;
        }
        if (NEGATIVE_CONFIRMATIONS.contains(normalized)) {
            return ConfirmationReply.REJECT;
        }
        if (CANCEL_COMMANDS.contains(normalized)) {
            return ConfirmationReply.CANCEL;
        }
        return ConfirmationReply.INVALID;
    }

    public boolean isCancelCommand(String input) {
        return CANCEL_COMMANDS.contains(normalize(input));
    }

    private String normalize(String input) {
        return (input == null ? "" : input)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[.!?,:;]+", "")
                .trim();
    }
}
