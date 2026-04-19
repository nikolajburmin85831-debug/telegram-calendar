package io.github.nadya.assistant.application.service;

import io.github.nadya.assistant.application.command.MessageHandlingContext;
import io.github.nadya.assistant.domain.calendar.CalendarActionType;
import io.github.nadya.assistant.domain.calendar.CalendarEventDraft;
import io.github.nadya.assistant.domain.household.HouseholdMember;
import io.github.nadya.assistant.domain.household.HouseholdNotificationSettings;
import io.github.nadya.assistant.ports.out.NotificationCommand;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class HouseholdNotificationService {

    private static final System.Logger LOGGER = System.getLogger(HouseholdNotificationService.class.getName());
    private static final Locale RUSSIAN = Locale.forLanguageTag("ru-RU");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMMM", RUSSIAN);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", RUSSIAN);

    private final HouseholdNotificationSettings settings;

    public HouseholdNotificationService(HouseholdNotificationSettings settings) {
        this.settings = settings;
    }

    public List<NotificationCommand> buildNotifications(
            MessageHandlingContext context,
            CalendarActionType actionType,
            CalendarEventDraft draft
    ) {
        if (!settings.enabled()) {
            LOGGER.log(
                    System.Logger.Level.DEBUG,
                    "Household notifications skipped: feature disabled for user {0}",
                    context.sourceMessage().userId().value()
            );
            return List.of();
        }

        HouseholdMember initiator = settings.findInitiator(
                context.sourceMessage().userId(),
                context.sourceMessage().conversationId()
        ).orElse(null);
        if (initiator == null) {
            LOGGER.log(
                    System.Logger.Level.DEBUG,
                    "Household notifications skipped: initiator user {0} / conversation {1} is not mapped to a household member",
                    context.sourceMessage().userId().value(),
                    context.sourceMessage().conversationId()
            );
            return List.of();
        }

        if (initiator.notifyMemberIds().isEmpty()) {
            LOGGER.log(
                    System.Logger.Level.DEBUG,
                    "Household notifications skipped for member {0}: no recipient members configured",
                    initiator.memberId()
            );
            return List.of();
        }

        LinkedHashSet<NotificationCommand> commands = new LinkedHashSet<>();
        for (String recipientMemberId : initiator.notifyMemberIds()) {
            HouseholdMember recipient = settings.findByMemberId(recipientMemberId).orElse(null);
            if (recipient == null) {
                LOGGER.log(
                        System.Logger.Level.WARNING,
                        "Household notifications skipped for member {0}: recipient member {1} is not configured",
                        initiator.memberId(),
                        recipientMemberId
                );
                continue;
            }
            if (recipient.memberId().equals(initiator.memberId())) {
                LOGGER.log(
                        System.Logger.Level.DEBUG,
                        "Household notifications skipped for member {0}: self-notification suppressed",
                        initiator.memberId()
                );
                continue;
            }
            if (recipient.conversationIds().isEmpty()) {
                LOGGER.log(
                        System.Logger.Level.WARNING,
                        "Household notifications skipped for member {0}: recipient member {1} has no chat ids configured",
                        initiator.memberId(),
                        recipient.memberId()
                );
                continue;
            }
            recipient.conversationIds().stream()
                    .filter(conversationId -> !initiator.conversationIds().contains(conversationId))
                    .map(conversationId -> new NotificationCommand(recipient.userId(), conversationId, formatMessage(initiator, actionType, draft)))
                    .forEach(commands::add);
        }

        if (commands.isEmpty()) {
            LOGGER.log(
                    System.Logger.Level.DEBUG,
                    "Household notifications skipped for member {0}: no deliverable recipient chats",
                    initiator.memberId()
            );
            return List.of();
        }
        LOGGER.log(
                System.Logger.Level.DEBUG,
                "Household notifications prepared for member {0}: {1} command(s)",
                initiator.memberId(),
                commands.size()
        );
        return List.copyOf(commands);
    }

    private String formatMessage(HouseholdMember initiator, CalendarActionType actionType, CalendarEventDraft draft) {
        return "%s %s: %s, %s".formatted(
                initiator.displayName(),
                actionLabel(actionType),
                draft.title(),
                scheduleLabel(draft)
        );
    }

    private String actionLabel(CalendarActionType actionType) {
        return switch (actionType) {
            case CREATE -> "создала событие";
            case UPDATE -> "перенесла событие";
            case CANCEL -> "отменила событие";
        };
    }

    private String scheduleLabel(CalendarEventDraft draft) {
        if (draft.start() == null) {
            return "без корректного времени";
        }
        if (draft.allDay()) {
            return DATE_FORMATTER.format(draft.start()) + ", весь день";
        }
        return DATE_FORMATTER.format(draft.start()) + " в " + TIME_FORMATTER.format(draft.start());
    }
}
