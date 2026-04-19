package io.github.nadya.assistant.application.handler;

import io.github.nadya.assistant.application.command.MessageHandlingContext;
import io.github.nadya.assistant.application.query.AgendaQuery;
import io.github.nadya.assistant.application.result.AgendaResult;
import io.github.nadya.assistant.application.result.HandlingOutcome;
import io.github.nadya.assistant.application.service.AgendaSummaryFormatter;
import io.github.nadya.assistant.domain.execution.ExecutionResult;
import io.github.nadya.assistant.ports.out.CalendarPort;

public final class ListAgendaHandler {

    private final CalendarPort calendarPort;
    private final AgendaSummaryFormatter agendaSummaryFormatter;

    public ListAgendaHandler(
            CalendarPort calendarPort,
            AgendaSummaryFormatter agendaSummaryFormatter
    ) {
        this.calendarPort = calendarPort;
        this.agendaSummaryFormatter = agendaSummaryFormatter;
    }

    public HandlingOutcome handle(MessageHandlingContext context, AgendaQuery agendaQuery) {
        AgendaResult agendaResult = new AgendaResult(
                agendaQuery,
                calendarPort.listEvents(agendaQuery.dateRange())
        );

        return new HandlingOutcome(
                ExecutionResult.completedWithoutResource(
                        agendaSummaryFormatter.format(agendaResult),
                        "agenda_listed_" + agendaQuery.range().name().toLowerCase()
                ),
                context.conversationState().completed()
        );
    }
}
