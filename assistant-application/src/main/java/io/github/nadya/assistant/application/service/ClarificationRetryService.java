package io.github.nadya.assistant.application.service;

import io.github.nadya.assistant.application.result.HandlingOutcome;
import io.github.nadya.assistant.domain.conversation.ClarificationRequest;
import io.github.nadya.assistant.domain.conversation.ConversationState;
import io.github.nadya.assistant.domain.execution.ExecutionGuardResult;
import io.github.nadya.assistant.domain.execution.ExecutionResult;
import io.github.nadya.assistant.domain.execution.GuardViolation;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ClarificationRetryService {

    private static final Set<String> RETRYABLE_GUARD_CODES = Set.of(
            "invalid_start_structure",
            "invalid_end_structure",
            "malformed_start",
            "invalid_time_range"
    );

    private static final Pattern TIME_TOKEN = Pattern.compile("(?<!\\d)(\\d{1,2}:\\d{2})(?!\\d)");
    private static final Pattern ISO_DATE_TOKEN = Pattern.compile("(?<!\\d)(\\d{4}-\\d{2}-\\d{2})(?!\\d)");
    private static final Pattern LOCAL_DATE_TOKEN = Pattern.compile("(?<!\\d)(\\d{1,2}[./-]\\d{1,2}(?:[./-]\\d{2,4})?)(?!\\d)");

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm");
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d.M.uuuu"),
            DateTimeFormatter.ofPattern("d.M.uu"),
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d/M/uu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("d-M-uu")
    );

    private final ClarificationRetrySettings settings;

    public ClarificationRetryService(ClarificationRetrySettings settings) {
        this.settings = settings;
    }

    public boolean isObviouslyInvalidInput(ConversationState state, String userInput) {
        if (!state.isAwaitingClarification() || state.clarificationRequest() == null) {
            return false;
        }

        return switch (clarificationKind(state.clarificationRequest())) {
            case "time" -> hasOnlyInvalidTimeCandidates(userInput);
            case "date" -> hasOnlyInvalidDateCandidates(userInput);
            default -> false;
        };
    }

    public boolean isRetryableGuardRejection(ConversationState state, ExecutionGuardResult guardResult) {
        if (!state.isAwaitingClarification() || state.clarificationRequest() == null) {
            return false;
        }
        if ("other".equals(clarificationKind(state.clarificationRequest()))) {
            return false;
        }
        return guardResult.violations().stream()
                .map(GuardViolation::code)
                .anyMatch(RETRYABLE_GUARD_CODES::contains);
    }

    public HandlingOutcome handleInvalidAttempt(ConversationState currentState) {
        ClarificationRequest currentRequest = currentState.clarificationRequest();
        int nextAttempt = currentState.invalidClarificationAttempts() + 1;

        if (nextAttempt >= settings.maxInvalidAttempts()) {
            return new HandlingOutcome(
                    ExecutionResult.failed(exhaustedMessage(currentRequest), "clarification_invalid_attempts_exhausted"),
                    currentState.failed()
            );
        }

        ClarificationRequest retryRequest = new ClarificationRequest(
                currentRequest.reason(),
                currentRequest.missingFields(),
                retryPrompt(currentRequest),
                currentRequest.pendingActionReference()
        );
        ConversationState nextState = currentState.awaitingRetry(
                currentState.status(),
                retryRequest,
                currentState.pendingAction(),
                nextAttempt
        );
        return new HandlingOutcome(
                ExecutionResult.clarificationRequested(
                        retryRequest.userFacingQuestion(),
                        "clarification_invalid_attempt",
                        nextState.status().name()
                ),
                nextState
        );
    }

    private boolean hasOnlyInvalidTimeCandidates(String userInput) {
        List<String> candidates = extractMatches(TIME_TOKEN, userInput);
        if (candidates.isEmpty()) {
            return false;
        }
        return candidates.stream().allMatch(this::isInvalidTimeToken);
    }

    private boolean hasOnlyInvalidDateCandidates(String userInput) {
        List<String> candidates = extractMatches(ISO_DATE_TOKEN, userInput);
        if (candidates.isEmpty()) {
            candidates = extractMatches(LOCAL_DATE_TOKEN, userInput);
        }
        if (candidates.isEmpty()) {
            return false;
        }
        return candidates.stream().allMatch(this::isInvalidDateToken);
    }

    private List<String> extractMatches(Pattern pattern, String userInput) {
        Matcher matcher = pattern.matcher(userInput == null ? "" : userInput);
        java.util.ArrayList<String> matches = new java.util.ArrayList<>();
        while (matcher.find()) {
            matches.add(matcher.group(1));
        }
        return List.copyOf(matches);
    }

    private boolean isInvalidTimeToken(String token) {
        try {
            LocalTime.parse(token, TIME_FORMAT);
            return false;
        } catch (DateTimeParseException exception) {
            return true;
        }
    }

    private boolean isInvalidDateToken(String token) {
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                LocalDate.parse(token, formatter);
                return false;
            } catch (DateTimeParseException ignored) {
                // Try the next supported date format.
            }
        }
        return true;
    }

    private String clarificationKind(ClarificationRequest clarificationRequest) {
        if (clarificationRequest == null) {
            return "other";
        }
        if ("time".equals(clarificationRequest.reason())
                || clarificationRequest.missingFields().contains("time")
                || clarificationRequest.missingFields().contains("time_is_range")) {
            return "time";
        }
        if ("date".equals(clarificationRequest.reason())
                || clarificationRequest.missingFields().contains("date")) {
            return "date";
        }
        return "other";
    }

    private String retryPrompt(ClarificationRequest clarificationRequest) {
        return switch (clarificationKind(clarificationRequest)) {
            case "time" -> "Время указано некорректно. Напишите время в формате ЧЧ:ММ, например 14:30.";
            case "date" -> "Дата указана некорректно. Напишите дату в формате ГГГГ-ММ-ДД, например 2026-04-17.";
            default -> "Данные указаны некорректно. Попробуйте ещё раз.";
        };
    }

    private String exhaustedMessage(ClarificationRequest clarificationRequest) {
        return switch (clarificationKind(clarificationRequest)) {
            case "time" ->
                    "Похоже, время несколько раз указано некорректно. Останавливаю текущий запрос. Попробуйте заново и укажите время в формате ЧЧ:ММ, например 14:30.";
            case "date" ->
                    "Похоже, дата несколько раз указана некорректно. Останавливаю текущий запрос. Попробуйте заново и укажите дату в формате ГГГГ-ММ-ДД, например 2026-04-17.";
            default -> "Похоже, данные несколько раз указаны некорректно. Останавливаю текущий запрос.";
        };
    }
}
