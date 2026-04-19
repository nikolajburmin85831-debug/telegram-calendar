package io.github.nadya.assistant.adapter.out.google.calendar.service;

import io.github.nadya.assistant.adapter.out.google.calendar.client.GoogleCalendarClient;
import io.github.nadya.assistant.adapter.out.google.calendar.config.GoogleCalendarProperties;
import io.github.nadya.assistant.adapter.out.google.calendar.mapper.GoogleCalendarEventMapper;
import io.github.nadya.assistant.adapter.out.google.calendar.mapper.GoogleCalendarRequestMapper;
import io.github.nadya.assistant.adapter.out.google.calendar.oauth.GoogleCalendarOAuthSupport;
import io.github.nadya.assistant.domain.calendar.CalendarAgendaEvent;
import io.github.nadya.assistant.domain.calendar.CalendarDateRange;
import io.github.nadya.assistant.domain.calendar.CalendarEventDraft;
import io.github.nadya.assistant.domain.calendar.CalendarEventReference;
import io.github.nadya.assistant.ports.out.CalendarPort;

import java.util.List;

public final class GoogleCalendarAdapter implements CalendarPort {

    private final GoogleCalendarClient googleCalendarClient;
    private final GoogleCalendarRequestMapper requestMapper;
    private final GoogleCalendarEventMapper eventMapper;
    private final GoogleCalendarOAuthSupport oAuthSupport;
    private final GoogleCalendarProperties properties;

    public GoogleCalendarAdapter(
            GoogleCalendarClient googleCalendarClient,
            GoogleCalendarRequestMapper requestMapper,
            GoogleCalendarEventMapper eventMapper,
            GoogleCalendarOAuthSupport oAuthSupport,
            GoogleCalendarProperties properties
    ) {
        this.googleCalendarClient = googleCalendarClient;
        this.requestMapper = requestMapper;
        this.eventMapper = eventMapper;
        this.oAuthSupport = oAuthSupport;
        this.properties = properties;
    }

    @Override
    public CalendarEventReference createEvent(CalendarEventDraft draft) {
        ensureReady();
        GoogleCalendarClient.InsertRequest request = requestMapper.map(draft, properties);
        GoogleCalendarClient.InsertResponse response = googleCalendarClient.insert(request);

        return new CalendarEventReference(response.eventId(), response.htmlLink());
    }

    @Override
    public List<CalendarAgendaEvent> listEvents(CalendarDateRange dateRange) {
        ensureReady();
        GoogleCalendarClient.ListRequest request = requestMapper.map(dateRange, properties);
        return googleCalendarClient.list(request).stream()
                .map(item -> eventMapper.map(item, dateRange.startInclusive().getZone()))
                .toList();
    }

    private void ensureReady() {
        if (!properties.enabled() && !properties.stubMode()) {
            throw new IllegalStateException("Google Calendar adapter is disabled and stub mode is off");
        }

        if (!properties.stubMode() && !oAuthSupport.isReady()) {
            throw new IllegalStateException("Google Calendar adapter is enabled but OAuth is not configured");
        }
    }
}
