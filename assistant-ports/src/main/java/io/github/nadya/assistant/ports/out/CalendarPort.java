package io.github.nadya.assistant.ports.out;

import io.github.nadya.assistant.domain.calendar.CalendarEventDraft;
import io.github.nadya.assistant.domain.calendar.CalendarEventReference;

public interface CalendarPort {

    CalendarEventReference createEvent(CalendarEventDraft draft);
}
