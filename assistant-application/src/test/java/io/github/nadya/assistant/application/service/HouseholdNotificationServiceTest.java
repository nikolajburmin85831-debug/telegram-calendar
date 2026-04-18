package io.github.nadya.assistant.application.service;

import io.github.nadya.assistant.application.command.MessageHandlingContext;
import io.github.nadya.assistant.domain.calendar.CalendarActionType;
import io.github.nadya.assistant.domain.calendar.CalendarEventDraft;
import io.github.nadya.assistant.domain.common.ChannelType;
import io.github.nadya.assistant.domain.common.ConfidenceScore;
import io.github.nadya.assistant.domain.common.Timezone;
import io.github.nadya.assistant.domain.conversation.ConversationState;
import io.github.nadya.assistant.domain.conversation.IncomingUserMessage;
import io.github.nadya.assistant.domain.execution.ExecutionApproval;
import io.github.nadya.assistant.domain.household.HouseholdMember;
import io.github.nadya.assistant.domain.household.HouseholdNotificationSettings;
import io.github.nadya.assistant.domain.intent.AssistantIntent;
import io.github.nadya.assistant.domain.intent.IntentInterpretation;
import io.github.nadya.assistant.domain.intent.IntentType;
import io.github.nadya.assistant.domain.user.ConfirmationPreference;
import io.github.nadya.assistant.domain.user.UserContext;
import io.github.nadya.assistant.domain.user.UserIdentity;
import io.github.nadya.assistant.ports.out.NotificationCommand;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HouseholdNotificationServiceTest {

    @Test
    void createByWifeNotifiesMe() {
        HouseholdNotificationService service = new HouseholdNotificationService(wifeToMeHousehold());

        List<NotificationCommand> notifications = service.buildNotifications(
                wifeContext("telegram-chat:202"),
                CalendarActionType.CREATE,
                sampleDraft()
        );

        assertEquals(1, notifications.size());
        assertEquals("telegram-chat:101", notifications.get(0).conversationId());
        assertTrue(notifications.get(0).text().contains("Жена создала событие"));
    }

    @Test
    void updateByWifeNotifiesMe() {
        HouseholdNotificationService service = new HouseholdNotificationService(wifeToMeHousehold());

        List<NotificationCommand> notifications = service.buildNotifications(
                wifeContext("telegram-chat:202"),
                CalendarActionType.UPDATE,
                sampleDraft()
        );

        assertEquals(1, notifications.size());
        assertTrue(notifications.get(0).text().contains("Жена перенесла событие"));
    }

    @Test
    void cancelByWifeNotifiesMe() {
        HouseholdNotificationService service = new HouseholdNotificationService(wifeToMeHousehold());

        List<NotificationCommand> notifications = service.buildNotifications(
                wifeContext("telegram-chat:202"),
                CalendarActionType.CANCEL,
                sampleDraft()
        );

        assertEquals(1, notifications.size());
        assertTrue(notifications.get(0).text().contains("Жена отменила событие"));
    }

    @Test
    void actionByMeDoesNotNotifyMyself() {
        HouseholdNotificationService service = new HouseholdNotificationService(new HouseholdNotificationSettings(
                true,
                Map.of(
                        "me", new HouseholdMember(
                                "me",
                                "Я",
                                new UserIdentity("telegram-user:42"),
                                List.of("telegram-chat:101"),
                                List.of("me")
                        )
                )
        ));

        List<NotificationCommand> notifications = service.buildNotifications(
                meContext(),
                CalendarActionType.CREATE,
                sampleDraft()
        );

        assertTrue(notifications.isEmpty());
    }

    @Test
    void missingRecipientConfigDoesNotCrashAndReturnsEmptyNotifications() {
        HouseholdNotificationService service = new HouseholdNotificationService(new HouseholdNotificationSettings(
                true,
                Map.of(
                        "wife", new HouseholdMember(
                                "wife",
                                "Жена",
                                new UserIdentity("telegram-user:84"),
                                List.of("telegram-chat:202"),
                                List.of("me")
                        )
                )
        ));

        List<NotificationCommand> notifications = service.buildNotifications(
                wifeContext("telegram-chat:202"),
                CalendarActionType.CREATE,
                sampleDraft()
        );

        assertTrue(notifications.isEmpty());
    }

    private HouseholdNotificationSettings wifeToMeHousehold() {
        return new HouseholdNotificationSettings(
                true,
                Map.of(
                        "me", new HouseholdMember(
                                "me",
                                "Я",
                                new UserIdentity("telegram-user:42"),
                                List.of("telegram-chat:101"),
                                List.of()
                        ),
                        "wife", new HouseholdMember(
                                "wife",
                                "Жена",
                                new UserIdentity("telegram-user:84"),
                                List.of("telegram-chat:202"),
                                List.of("me")
                        )
                )
        );
    }

    private MessageHandlingContext wifeContext(String conversationId) {
        return contextFor("telegram-user:84", conversationId);
    }

    private MessageHandlingContext meContext() {
        return contextFor("telegram-user:42", "telegram-chat:101");
    }

    private MessageHandlingContext contextFor(String userId, String conversationId) {
        IncomingUserMessage message = new IncomingUserMessage(
                "internal-household",
                "household",
                new UserIdentity(userId),
                ChannelType.TELEGRAM,
                conversationId,
                "Создай событие",
                Instant.parse("2026-04-16T20:15:30Z")
        );
        return new MessageHandlingContext(
                message,
                message,
                new UserContext(
                        message.userId(),
                        new Timezone("Europe/Moscow"),
                        "ru",
                        ConfirmationPreference.AUTO_EXECUTE,
                        Duration.ofHours(1),
                        conversationId
                ),
                ConversationState.idle(conversationId, message.userId()),
                new IntentInterpretation(
                        new AssistantIntent(IntentType.CREATE_CALENDAR_EVENT, Map.of("title", "стоматолог")),
                        new ConfidenceScore(0.95d),
                        List.of(),
                        List.of(),
                        true
                ),
                ExecutionApproval.NOT_CONFIRMED
        );
    }

    private CalendarEventDraft sampleDraft() {
        return new CalendarEventDraft(
                "Стоматолог",
                "Created from assistant request",
                ZonedDateTime.parse("2026-04-17T14:00:00+03:00"),
                ZonedDateTime.parse("2026-04-17T15:00:00+03:00"),
                false,
                new Timezone("Europe/Moscow"),
                null,
                "",
                Map.of()
        );
    }
}
