package io.github.nadya.assistant.adapter.out.google.calendar.mapper;

import io.github.nadya.assistant.adapter.out.google.calendar.config.GoogleCalendarProperties;
import io.github.nadya.assistant.domain.calendar.CalendarEventDraft;
import io.github.nadya.assistant.domain.common.Timezone;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoogleCalendarRequestMapperTest {

    private final GoogleCalendarRequestMapper mapper = new GoogleCalendarRequestMapper();

    @Test
    void shouldFormatTimedEventsAsRfc3339WithSeconds() {
        CalendarEventDraft draft = new CalendarEventDraft(
                "К стоматологу",
                "Created by test",
                ZonedDateTime.parse("2026-04-18T14:00:00+03:00[Europe/Moscow]"),
                ZonedDateTime.parse("2026-04-18T14:30:00+03:00[Europe/Moscow]"),
                false,
                new Timezone("Europe/Moscow"),
                null,
                "Clinic",
                Map.of("source", "test")
        );

        var request = mapper.map(draft, new GoogleCalendarProperties(true, "primary", false, "https://www.googleapis.com/calendar/v3"));

        assertEquals("2026-04-18T14:00:00+03:00", request.startDateTime());
        assertEquals("2026-04-18T14:30:00+03:00", request.endDateTime());
    }
}
