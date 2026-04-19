package io.github.nadya.assistant.application.service;

import io.github.nadya.assistant.application.command.MessageHandlingContext;
import io.github.nadya.assistant.application.query.AgendaQuery;
import io.github.nadya.assistant.domain.common.ChannelType;
import io.github.nadya.assistant.domain.common.ConfidenceScore;
import io.github.nadya.assistant.domain.common.Timezone;
import io.github.nadya.assistant.domain.conversation.ConversationState;
import io.github.nadya.assistant.domain.conversation.IncomingUserMessage;
import io.github.nadya.assistant.domain.execution.ExecutionApproval;
import io.github.nadya.assistant.domain.intent.AssistantIntent;
import io.github.nadya.assistant.domain.intent.IntentInterpretation;
import io.github.nadya.assistant.domain.intent.IntentType;
import io.github.nadya.assistant.domain.user.ConfirmationPreference;
import io.github.nadya.assistant.domain.user.UserContext;
import io.github.nadya.assistant.domain.user.UserIdentity;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgendaQueryFactoryTest {

    private final AgendaQueryFactory factory = new AgendaQueryFactory();

    @Test
    void shouldMapTodayToCurrentLocalDayRange() {
        AgendaQuery query = factory.build(contextFor("today"));

        assertEquals("today", query.range().entityValue());
        assertEquals(
                ZonedDateTime.parse("2026-04-16T00:00:00+03:00[Europe/Moscow]"),
                query.dateRange().startInclusive()
        );
        assertEquals(
                ZonedDateTime.parse("2026-04-17T00:00:00+03:00[Europe/Moscow]"),
                query.dateRange().endExclusive()
        );
    }

    @Test
    void shouldMapTomorrowToNextLocalDayRange() {
        AgendaQuery query = factory.build(contextFor("tomorrow"));

        assertEquals("tomorrow", query.range().entityValue());
        assertEquals(
                ZonedDateTime.parse("2026-04-17T00:00:00+03:00[Europe/Moscow]"),
                query.dateRange().startInclusive()
        );
        assertEquals(
                ZonedDateTime.parse("2026-04-18T00:00:00+03:00[Europe/Moscow]"),
                query.dateRange().endExclusive()
        );
    }

    private MessageHandlingContext contextFor(String agendaRange) {
        IncomingUserMessage message = new IncomingUserMessage(
                "internal-agenda",
                "external-agenda",
                new UserIdentity("telegram-user:42"),
                ChannelType.TELEGRAM,
                "telegram-chat:101",
                "Что сегодня",
                Instant.parse("2026-04-16T20:15:30Z")
        );
        UserContext userContext = new UserContext(
                new UserIdentity("telegram-user:42"),
                new Timezone("Europe/Moscow"),
                "ru",
                ConfirmationPreference.AUTO_EXECUTE,
                Duration.ofHours(1),
                "telegram-chat:101"
        );
        IntentInterpretation interpretation = new IntentInterpretation(
                new AssistantIntent(IntentType.LIST_AGENDA, Map.of("agendaRange", agendaRange)),
                new ConfidenceScore(0.95d),
                List.of(),
                List.of(),
                true
        );

        return new MessageHandlingContext(
                message,
                message,
                userContext,
                ConversationState.idle("telegram-chat:101", new UserIdentity("telegram-user:42")),
                interpretation,
                ExecutionApproval.NOT_CONFIRMED
        );
    }
}
