package io.github.nadya.assistant.application.service;

import io.github.nadya.assistant.application.command.MessageHandlingContext;
import io.github.nadya.assistant.domain.calendar.CalendarActionType;
import io.github.nadya.assistant.domain.calendar.CalendarEventDraft;
import io.github.nadya.assistant.domain.conversation.ClarificationRequest;
import io.github.nadya.assistant.domain.conversation.PendingConfirmation;
import io.github.nadya.assistant.domain.execution.ExecutionApproval;
import io.github.nadya.assistant.domain.execution.ExecutionGuardResult;
import io.github.nadya.assistant.domain.execution.GuardViolation;
import io.github.nadya.assistant.domain.execution.RiskLevel;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public final class CalendarExecutionGuard {

    private final CalendarExecutionGuardSettings settings;
    private final Clock clock;

    public CalendarExecutionGuard(CalendarExecutionGuardSettings settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    public ExecutionGuardResult evaluate(
            CalendarActionType actionType,
            CalendarEventDraft draft,
            MessageHandlingContext context
    ) {
        List<GuardViolation> rejectViolations = new ArrayList<>();
        List<GuardViolation> clarificationViolations = new ArrayList<>();
        List<GuardViolation> confirmationViolations = new ArrayList<>();

        validateContent(draft, rejectViolations);
        ZoneId effectiveZone = validateTimezone(draft, rejectViolations);
        validateScheduleShape(draft, rejectViolations, clarificationViolations, effectiveZone);
        validateRangeAndDuration(draft, rejectViolations);
        validateConfirmationPolicies(draft, context.executionApproval(), confirmationViolations, effectiveZone);

        if (!rejectViolations.isEmpty()) {
            return ExecutionGuardResult.reject(
                    maxRisk(rejectViolations),
                    rejectViolations,
                    rejectViolations.get(0).message()
            );
        }
        if (!clarificationViolations.isEmpty()) {
            return ExecutionGuardResult.requireClarification(
                    maxRisk(clarificationViolations),
                    clarificationViolations,
                    clarificationRequest(context, clarificationViolations)
            );
        }
        if (!confirmationViolations.isEmpty()) {
            return ExecutionGuardResult.requireConfirmation(
                    maxRisk(confirmationViolations),
                    confirmationViolations,
                    pendingConfirmation(actionType, draft, context, confirmationViolations)
            );
        }
        return ExecutionGuardResult.allow();
    }

    private void validateContent(CalendarEventDraft draft, List<GuardViolation> rejectViolations) {
        if (draft.title() == null || draft.title().isBlank()) {
            rejectViolations.add(reject("missing_title", "Не могу безопасно выполнить действие без названия события."));
            return;
        }
        if (draft.title().length() > settings.maxTitleLength()) {
            rejectViolations.add(reject(
                    "title_too_long",
                    "Не могу безопасно выполнить действие: название события слишком длинное."
            ));
        }
        if (containsDisallowedControlCharacters(draft.title())) {
            rejectViolations.add(reject(
                    "title_control_characters",
                    "Не могу безопасно выполнить действие: название события содержит недопустимые символы."
            ));
        }
        if (draft.description() != null && draft.description().length() > settings.maxDescriptionLength()) {
            rejectViolations.add(reject(
                    "description_too_long",
                    "Не могу безопасно выполнить действие: описание события слишком длинное."
            ));
        }
    }

    private ZoneId validateTimezone(CalendarEventDraft draft, List<GuardViolation> rejectViolations) {
        if (draft.timezone() == null || draft.timezone().value() == null || draft.timezone().value().isBlank()) {
            rejectViolations.add(reject("missing_timezone", "Не могу безопасно выполнить действие: не удалось определить часовой пояс."));
            return null;
        }
        if (hasParseError(draft, CalendarDraftMetadataKeys.PARSE_ERROR_TIMEZONE)) {
            rejectViolations.add(reject("invalid_timezone", "Не могу безопасно выполнить действие: часовой пояс указан некорректно."));
            return null;
        }
        try {
            return draft.timezone().toZoneId();
        } catch (RuntimeException exception) {
            rejectViolations.add(reject("invalid_timezone", "Не могу безопасно выполнить действие: часовой пояс указан некорректно."));
            return null;
        }
    }

    private void validateScheduleShape(
            CalendarEventDraft draft,
            List<GuardViolation> rejectViolations,
            List<GuardViolation> clarificationViolations,
            ZoneId effectiveZone
    ) {
        if (draft.start() == null) {
            if (hasParseError(draft, CalendarDraftMetadataKeys.PARSE_ERROR_START_DATE)
                    || hasParseError(draft, CalendarDraftMetadataKeys.PARSE_ERROR_START_TIME)) {
                rejectViolations.add(reject(
                        "invalid_start_structure",
                        "Не могу безопасно выполнить действие: дата или время указаны некорректно."
                ));
                return;
            }
            if (!hasMetadata(draft, CalendarDraftMetadataKeys.RAW_START_DATE)) {
                clarificationViolations.add(clarify("missing_start_date", "Уточните дату события."));
                return;
            }
            if (!draft.allDay() && !hasMetadata(draft, CalendarDraftMetadataKeys.RAW_START_TIME)) {
                clarificationViolations.add(clarify("missing_start_time", "Уточните время события."));
                return;
            }
            rejectViolations.add(reject("malformed_start", "Не могу безопасно выполнить действие: расписание события повреждено."));
            return;
        }

        if (draft.end() == null) {
            if (hasParseError(draft, CalendarDraftMetadataKeys.PARSE_ERROR_END_DATE)
                    || hasParseError(draft, CalendarDraftMetadataKeys.PARSE_ERROR_END_TIME)
                    || hasParseError(draft, CalendarDraftMetadataKeys.PARSE_ERROR_DURATION)) {
                rejectViolations.add(reject(
                        "invalid_end_structure",
                        "Не могу безопасно выполнить действие: окончание или длительность указаны некорректно."
                ));
                return;
            }
            rejectViolations.add(reject("missing_end", "Не могу безопасно выполнить действие: не удалось определить окончание события."));
            return;
        }

        if (effectiveZone != null) {
            if (!matchesEffectiveZone(draft.start(), effectiveZone)
                    || !matchesEffectiveZone(draft.end(), effectiveZone)) {
                rejectViolations.add(reject(
                        "timezone_mismatch",
                        "Не могу безопасно выполнить действие: время события не согласовано с часовым поясом."
                ));
            }
        }

        if (draft.allDay()) {
            if (hasMetadata(draft, CalendarDraftMetadataKeys.RAW_START_TIME)) {
                clarificationViolations.add(clarify(
                        "all_day_time_conflict",
                        "Уточните, событие на весь день или в конкретное время."
                ));
            }
            if (!draft.start().toLocalTime().equals(LocalTime.MIDNIGHT)
                    || !draft.end().toLocalTime().equals(LocalTime.MIDNIGHT)) {
                rejectViolations.add(reject(
                        "invalid_all_day_structure",
                        "Не могу безопасно выполнить действие: формат события на весь день некорректен."
                ));
            }
        }
    }

    private void validateRangeAndDuration(CalendarEventDraft draft, List<GuardViolation> rejectViolations) {
        if (draft.start() == null || draft.end() == null) {
            return;
        }

        if (!draft.end().isAfter(draft.start())) {
            rejectViolations.add(reject(
                    "invalid_time_range",
                    "Не могу безопасно выполнить действие: окончание события должно быть позже начала."
            ));
            return;
        }

        Duration duration = Duration.between(draft.start(), draft.end());
        if (duration.compareTo(settings.maxEventDuration()) > 0) {
            rejectViolations.add(reject(
                    "duration_too_long",
                    "Не могу безопасно выполнить действие: длительность события превышает безопасный лимит."
            ));
        }
    }

    private void validateConfirmationPolicies(
            CalendarEventDraft draft,
            ExecutionApproval executionApproval,
            List<GuardViolation> confirmationViolations,
            ZoneId effectiveZone
    ) {
        if (executionApproval == ExecutionApproval.CONFIRMED) {
            return;
        }

        if (draft.recurrenceRule() != null && draft.recurrenceRule().value() != null && !draft.recurrenceRule().value().isBlank()) {
            confirmationViolations.add(confirm(
                    "recurrence_requires_confirmation",
                    "Для повторяющегося события нужно подтверждение перед выполнением."
            ));
        }

        if (draft.start() != null) {
            ZoneId comparisonZone = effectiveZone == null ? draft.start().getZone() : effectiveZone;
            ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(comparisonZone);
            ZonedDateTime confirmationBoundary = now.plusDays(settings.confirmationHorizonDays());
            if (draft.start().withZoneSameInstant(comparisonZone).isAfter(confirmationBoundary)) {
                confirmationViolations.add(confirm(
                        "far_future_requires_confirmation",
                        "Для событий слишком далеко в будущем нужно подтверждение перед выполнением."
                ));
            }
        }
    }

    private ClarificationRequest clarificationRequest(
            MessageHandlingContext context,
            List<GuardViolation> clarificationViolations
    ) {
        GuardViolation primaryViolation = clarificationViolations.get(0);
        String reason = primaryViolation.code().contains("date") ? "date" : "time";
        List<String> missingFields = reason.equals("date") ? List.of("date") : List.of("time");
        return new ClarificationRequest(
                reason,
                missingFields,
                primaryViolation.message(),
                context.sourceMessage().internalMessageId()
        );
    }

    private PendingConfirmation pendingConfirmation(
            CalendarActionType actionType,
            CalendarEventDraft draft,
            MessageHandlingContext context,
            List<GuardViolation> confirmationViolations
    ) {
        StringJoiner reasonJoiner = new StringJoiner(" ");
        confirmationViolations.stream()
                .map(GuardViolation::message)
                .forEach(reasonJoiner::add);
        String actionSummary = switch (actionType) {
            case CREATE -> "Создать событие \"%s\" (%s)".formatted(draft.title(), scheduleLabel(draft));
            case UPDATE -> "Перенести событие \"%s\" (%s)".formatted(draft.title(), scheduleLabel(draft));
            case CANCEL -> "Отменить событие \"%s\" (%s)".formatted(draft.title(), scheduleLabel(draft));
        };

        return new PendingConfirmation(
                "guard-" + context.sourceMessage().internalMessageId(),
                context.sourceMessage().userId(),
                actionSummary,
                reasonJoiner + " " + actionSummary + ". Ответьте \"да\" или \"нет\".",
                context.triggeringMessage().receivedAt().plus(Duration.ofMinutes(30)),
                context.sourceMessage().internalMessageId()
        );
    }

    private String scheduleLabel(CalendarEventDraft draft) {
        if (draft.start() == null) {
            return "без корректного времени";
        }
        if (draft.allDay()) {
            return draft.start().toLocalDate() + ", весь день";
        }
        return "%s %s".formatted(draft.start().toLocalDate(), draft.start().toLocalTime());
    }

    private boolean matchesEffectiveZone(ZonedDateTime dateTime, ZoneId effectiveZone) {
        if (dateTime == null || effectiveZone == null) {
            return true;
        }
        if (dateTime.getZone().equals(effectiveZone)) {
            return true;
        }
        return dateTime.getOffset().equals(effectiveZone.getRules().getOffset(dateTime.toInstant()));
    }

    private boolean containsDisallowedControlCharacters(String value) {
        return value.chars().anyMatch(character -> Character.isISOControl(character) && character != '\n' && character != '\t');
    }

    private boolean hasParseError(CalendarEventDraft draft, String key) {
        return "true".equalsIgnoreCase(draft.metadata(key));
    }

    private boolean hasMetadata(CalendarEventDraft draft, String key) {
        return !draft.metadata(key).isBlank();
    }

    private GuardViolation reject(String code, String message) {
        return new GuardViolation(code, message, RiskLevel.HIGH);
    }

    private GuardViolation clarify(String code, String message) {
        return new GuardViolation(code, message, RiskLevel.MEDIUM);
    }

    private GuardViolation confirm(String code, String message) {
        return new GuardViolation(code, message, RiskLevel.MEDIUM);
    }

    private RiskLevel maxRisk(List<GuardViolation> violations) {
        RiskLevel result = RiskLevel.LOW;
        for (GuardViolation violation : violations) {
            result = RiskLevel.max(result, violation.riskLevel());
        }
        return result;
    }
}
