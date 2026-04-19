package io.github.nadya.assistant.adapter.out.google.calendar.service;

import io.github.nadya.assistant.adapter.out.google.calendar.client.GoogleCalendarClient;
import io.github.nadya.assistant.adapter.out.google.calendar.config.GoogleCalendarProperties;
import io.github.nadya.assistant.adapter.out.google.calendar.mapper.GoogleCalendarEventMapper;
import io.github.nadya.assistant.adapter.out.google.calendar.mapper.GoogleCalendarRequestMapper;
import io.github.nadya.assistant.adapter.out.google.calendar.oauth.GoogleCalendarOAuthSupport;
import io.github.nadya.assistant.adapter.out.google.calendar.oauth.GoogleOAuthProperties;
import io.github.nadya.assistant.domain.calendar.CalendarDateRange;
import io.github.nadya.assistant.domain.calendar.CalendarEventDraft;
import io.github.nadya.assistant.domain.common.Timezone;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleCalendarAdapterTest {

    @Test
    void shouldFailCleanlyWhenRealGoogleCalendarModeHasNoOAuthCredentials() {
        GoogleCalendarAdapter adapter = new GoogleCalendarAdapter(
                new NoOpGoogleCalendarClient(),
                new GoogleCalendarRequestMapper(),
                new GoogleCalendarEventMapper(),
                new GoogleCalendarOAuthSupport(emptyOAuthProperties()),
                new GoogleCalendarProperties(true, "primary", false, "https://www.googleapis.com/calendar/v3")
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> adapter.createEvent(sampleDraft())
        );

        assertEquals("Google Calendar adapter is enabled but OAuth is not configured", exception.getMessage());
    }

    @Test
    void shouldAllowStubModeWithoutOAuthSetup() {
        AtomicBoolean clientCalled = new AtomicBoolean(false);
        GoogleCalendarAdapter adapter = new GoogleCalendarAdapter(
                new GoogleCalendarClient() {
                    @Override
                    public InsertResponse insert(InsertRequest request) {
                        clientCalled.set(true);
                        return new InsertResponse("stub-event", "stub://event");
                    }

                    @Override
                    public List<ListItem> list(ListRequest request) {
                        return List.of();
                    }
                },
                new GoogleCalendarRequestMapper(),
                new GoogleCalendarEventMapper(),
                new GoogleCalendarOAuthSupport(emptyOAuthProperties()),
                new GoogleCalendarProperties(false, "primary", true, "https://www.googleapis.com/calendar/v3")
        );

        var reference = adapter.createEvent(sampleDraft());

        assertTrue(clientCalled.get());
        assertEquals("stub-event", reference.externalId());
        assertEquals("stub://event", reference.humanReadableReference());
    }

    @Test
    void shouldMapAgendaItemsToProviderAgnosticDomainEvents() {
        GoogleCalendarAdapter adapter = new GoogleCalendarAdapter(
                new GoogleCalendarClient() {
                    @Override
                    public InsertResponse insert(InsertRequest request) {
                        return new InsertResponse("unused", "unused");
                    }

                    @Override
                    public List<ListItem> list(ListRequest request) {
                        return List.of(
                                new ListItem(
                                        "Командный созвон",
                                        "2026-04-17T09:00:00+03:00",
                                        null,
                                        "2026-04-17T09:30:00+03:00",
                                        null,
                                        "Europe/Moscow",
                                        "Zoom"
                                ),
                                new ListItem(
                                        "Оплатить аренду",
                                        null,
                                        "2026-04-17",
                                        null,
                                        "2026-04-18",
                                        "Europe/Moscow",
                                        ""
                                )
                        );
                    }
                },
                new GoogleCalendarRequestMapper(),
                new GoogleCalendarEventMapper(),
                new GoogleCalendarOAuthSupport(emptyOAuthProperties()),
                new GoogleCalendarProperties(false, "primary", true, "https://www.googleapis.com/calendar/v3")
        );

        var events = adapter.listEvents(CalendarDateRange.forDay(LocalDate.parse("2026-04-17"), ZoneId.of("Europe/Moscow")));

        assertEquals(2, events.size());
        assertEquals("Командный созвон", events.get(0).title());
        assertEquals("Zoom", events.get(0).location());
        assertTrue(events.get(1).allDay());
        assertEquals("Оплатить аренду", events.get(1).title());
    }

    private GoogleOAuthProperties emptyOAuthProperties() {
        return new GoogleOAuthProperties(
                "",
                "",
                "",
                "",
                "https://oauth2.googleapis.com/token",
                List.of(),
                "test"
        );
    }

    private CalendarEventDraft sampleDraft() {
        ZonedDateTime start = ZonedDateTime.parse("2026-04-17T10:00:00+03:00[Europe/Moscow]");
        return new CalendarEventDraft(
                "Демо",
                "Created by test",
                start,
                start.plusHours(1),
                false,
                new Timezone("Europe/Moscow"),
                null,
                "Zoom",
                Map.of("source", "test")
        );
    }

    private static final class NoOpGoogleCalendarClient implements GoogleCalendarClient {
        @Override
        public InsertResponse insert(InsertRequest request) {
            return new InsertResponse("unused", "unused");
        }

        @Override
        public List<ListItem> list(ListRequest request) {
            return List.of();
        }
    }
}
