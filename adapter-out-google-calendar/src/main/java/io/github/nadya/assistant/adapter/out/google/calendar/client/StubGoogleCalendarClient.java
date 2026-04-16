package io.github.nadya.assistant.adapter.out.google.calendar.client;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class StubGoogleCalendarClient implements GoogleCalendarClient {

    private static final System.Logger LOGGER = System.getLogger(StubGoogleCalendarClient.class.getName());

    private final AtomicLong sequence = new AtomicLong(1L);
    private final List<InsertRequest> insertedRequests = new CopyOnWriteArrayList<>();

    @Override
    public InsertResponse insert(InsertRequest request) {
        insertedRequests.add(request);
        long id = sequence.getAndIncrement();
        String eventId = "stub-google-event-" + id;
        String link = "google-calendar://events/" + eventId;
        LOGGER.log(System.Logger.Level.INFO, "Stub Google Calendar event created: {0}", request.summary());
        return new InsertResponse(eventId, link);
    }

    public List<InsertRequest> insertedRequests() {
        return List.copyOf(insertedRequests);
    }
}
