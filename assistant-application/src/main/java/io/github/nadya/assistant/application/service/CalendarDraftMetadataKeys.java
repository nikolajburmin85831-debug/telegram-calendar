package io.github.nadya.assistant.application.service;

final class CalendarDraftMetadataKeys {

    static final String RAW_TITLE = "assistant.rawTitle";
    static final String RAW_START_DATE = "assistant.rawStartDate";
    static final String RAW_START_TIME = "assistant.rawStartTime";
    static final String RAW_END_DATE = "assistant.rawEndDate";
    static final String RAW_END_TIME = "assistant.rawEndTime";
    static final String RAW_DURATION_MINUTES = "assistant.rawDurationMinutes";
    static final String RAW_TIMEZONE = "assistant.rawTimezone";
    static final String RAW_RECURRENCE_RULE = "assistant.rawRecurrenceRule";
    static final String RAW_ALL_DAY = "assistant.rawAllDay";

    static final String PARSE_ERROR_START_DATE = "assistant.parseError.startDate";
    static final String PARSE_ERROR_START_TIME = "assistant.parseError.startTime";
    static final String PARSE_ERROR_END_DATE = "assistant.parseError.endDate";
    static final String PARSE_ERROR_END_TIME = "assistant.parseError.endTime";
    static final String PARSE_ERROR_DURATION = "assistant.parseError.duration";
    static final String PARSE_ERROR_TIMEZONE = "assistant.parseError.timezone";

    private CalendarDraftMetadataKeys() {
    }
}
