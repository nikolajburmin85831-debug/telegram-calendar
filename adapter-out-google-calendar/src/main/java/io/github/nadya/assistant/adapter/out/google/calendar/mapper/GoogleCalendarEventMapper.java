package io.github.nadya.assistant.adapter.out.google.calendar.mapper;

import io.github.nadya.assistant.adapter.out.google.calendar.client.GoogleCalendarClient;
import io.github.nadya.assistant.domain.calendar.CalendarAgendaEvent;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class GoogleCalendarEventMapper {

    public CalendarAgendaEvent map(GoogleCalendarClient.ListItem item, ZoneId fallbackZone) {
        ZoneId zoneId = resolveZone(item.timeZone(), fallbackZone);
        boolean allDay = item.startDate() != null && !item.startDate().isBlank();

        ZonedDateTime start = allDay
                ? LocalDate.parse(item.startDate()).atStartOfDay(zoneId)
                : OffsetDateTime.parse(item.startDateTime()).atZoneSameInstant(zoneId);
        ZonedDateTime end = allDay
                ? resolveAllDayEnd(item.endDate(), start, zoneId)
                : OffsetDateTime.parse(item.endDateTime()).atZoneSameInstant(zoneId);

        return new CalendarAgendaEvent(
                normalizeTitle(item.summary()),
                start,
                end,
                allDay,
                item.location()
        );
    }

    private ZoneId resolveZone(String rawTimeZone, ZoneId fallbackZone) {
        if (rawTimeZone == null || rawTimeZone.isBlank()) {
            return fallbackZone;
        }
        try {
            return ZoneId.of(rawTimeZone);
        } catch (RuntimeException exception) {
            return fallbackZone;
        }
    }

    private String normalizeTitle(String rawTitle) {
        if (rawTitle == null || rawTitle.isBlank()) {
            return "Без названия";
        }
        return rawTitle.trim();
    }

    private ZonedDateTime resolveAllDayEnd(String rawEndDate, ZonedDateTime start, ZoneId zoneId) {
        if (rawEndDate == null || rawEndDate.isBlank()) {
            return start.plusDays(1);
        }
        return LocalDate.parse(rawEndDate).atStartOfDay(zoneId);
    }
}
