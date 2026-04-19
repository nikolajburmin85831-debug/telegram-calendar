package io.github.nadya.assistant.ports.out;

import io.github.nadya.assistant.domain.calendar.CalendarAgendaEvent;
import io.github.nadya.assistant.domain.calendar.CalendarDateRange;
import io.github.nadya.assistant.domain.calendar.CalendarEventDraft;
import io.github.nadya.assistant.domain.calendar.CalendarEventReference;

import java.util.List;

public interface CalendarPort {

    CalendarEventReference createEvent(CalendarEventDraft draft);

    List<CalendarAgendaEvent> listEvents(CalendarDateRange dateRange);
}
