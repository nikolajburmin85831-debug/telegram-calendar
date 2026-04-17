package io.github.nadya.assistant.adapter.out.google.calendar.service;

import io.github.nadya.assistant.adapter.out.google.calendar.client.GoogleCalendarClient;
import io.github.nadya.assistant.adapter.out.google.calendar.config.GoogleCalendarProperties;
import io.github.nadya.assistant.adapter.out.google.calendar.mapper.GoogleCalendarRequestMapper;
import io.github.nadya.assistant.adapter.out.google.calendar.oauth.GoogleCalendarOAuthSupport;
import io.github.nadya.assistant.domain.calendar.CalendarEventDraft;
import io.github.nadya.assistant.domain.calendar.CalendarEventReference;
import io.github.nadya.assistant.ports.out.CalendarPort;

public final class GoogleCalendarAdapter implements CalendarPort {

    private final GoogleCalendarClient googleCalendarClient;
    private final GoogleCalendarRequestMapper requestMapper;
    private final GoogleCalendarOAuthSupport oAuthSupport;
    private final GoogleCalendarProperties properties;

    public GoogleCalendarAdapter(
            GoogleCalendarClient googleCalendarClient,
            GoogleCalendarRequestMapper requestMapper,
            GoogleCalendarOAuthSupport oAuthSupport,
            GoogleCalendarProperties properties
    ) {
        this.googleCalendarClient = googleCalendarClient;
        this.requestMapper = requestMapper;
        this.oAuthSupport = oAuthSupport;
        this.properties = properties;
    }

    @Override
    public CalendarEventReference createEvent(CalendarEventDraft draft) {
        if (!properties.enabled() && !properties.stubMode()) {
            throw new IllegalStateException("Google Calendar adapter is disabled and stub mode is off");
        }

        if (!properties.stubMode() && !oAuthSupport.isReady()) {
            throw new IllegalStateException("Google Calendar adapter is enabled but OAuth is not configured");
        }

        GoogleCalendarClient.InsertRequest request = requestMapper.map(draft, properties);
        GoogleCalendarClient.InsertResponse response = googleCalendarClient.insert(request);

        return new CalendarEventReference(response.eventId(), response.htmlLink());
    }
}
