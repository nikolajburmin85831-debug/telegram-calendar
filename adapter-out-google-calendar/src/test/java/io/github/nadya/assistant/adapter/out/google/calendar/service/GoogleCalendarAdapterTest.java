package io.github.nadya.assistant.adapter.out.google.calendar.service;

import io.github.nadya.assistant.adapter.out.google.calendar.client.GoogleCalendarClient;
import io.github.nadya.assistant.adapter.out.google.calendar.config.GoogleCalendarProperties;
import io.github.nadya.assistant.adapter.out.google.calendar.mapper.GoogleCalendarRequestMapper;
import io.github.nadya.assistant.adapter.out.google.calendar.oauth.GoogleCalendarOAuthSupport;
import io.github.nadya.assistant.domain.calendar.CalendarEventDraft;
import io.github.nadya.assistant.domain.common.Timezone;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleCalendarAdapterTest {

    @Test
    void shouldFailCleanlyWhenRealGoogleCalendarModeHasNoAccessToken() {
        GoogleCalendarAdapter adapter = new GoogleCalendarAdapter(
                request -> new GoogleCalendarClient.InsertResponse("unexpected", "unexpected"),
                new GoogleCalendarRequestMapper(),
                new GoogleCalendarOAuthSupport(),
                new GoogleCalendarProperties(true, "primary", false, "", "https://www.googleapis.com/calendar/v3")
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
                request -> {
                    clientCalled.set(true);
                    return new GoogleCalendarClient.InsertResponse("stub-event", "stub://event");
                },
                new GoogleCalendarRequestMapper(),
                new GoogleCalendarOAuthSupport(),
                new GoogleCalendarProperties(false, "primary", true, "", "https://www.googleapis.com/calendar/v3")
        );

        var reference = adapter.createEvent(sampleDraft());

        assertTrue(clientCalled.get());
        assertEquals("stub-event", reference.externalId());
        assertEquals("stub://event", reference.humanReadableReference());
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
}
