package io.github.nadya.assistant.application.service;

import io.github.nadya.assistant.application.command.MessageHandlingContext;
import io.github.nadya.assistant.domain.calendar.CalendarEventDraft;
import io.github.nadya.assistant.domain.common.RecurrenceRule;
import io.github.nadya.assistant.domain.common.Timezone;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CalendarEventDraftFactory {

    public CalendarEventDraft build(MessageHandlingContext context) {
        Map<String, String> entities = context.interpretation().assistantIntent().entities();
        LinkedHashMap<String, String> metadata = baseMetadata(context);

        captureRaw(metadata, CalendarDraftMetadataKeys.RAW_TITLE, entities.get("title"));
        captureRaw(metadata, CalendarDraftMetadataKeys.RAW_START_DATE, entities.get("startDate"));
        captureRaw(metadata, CalendarDraftMetadataKeys.RAW_START_TIME, entities.get("startTime"));
        captureRaw(metadata, CalendarDraftMetadataKeys.RAW_END_DATE, entities.get("endDate"));
        captureRaw(metadata, CalendarDraftMetadataKeys.RAW_END_TIME, entities.get("endTime"));
        captureRaw(metadata, CalendarDraftMetadataKeys.RAW_DURATION_MINUTES, entities.get("durationMinutes"));
        captureRaw(metadata, CalendarDraftMetadataKeys.RAW_TIMEZONE, entities.get("timezone"));
        captureRaw(metadata, CalendarDraftMetadataKeys.RAW_RECURRENCE_RULE, recurrenceValue(entities));
        captureRaw(metadata, CalendarDraftMetadataKeys.RAW_ALL_DAY, entities.get("allDay"));

        boolean allDay = Boolean.parseBoolean(entities.getOrDefault("allDay", "false"));
        Timezone timezone = resolveTimezone(entities.get("timezone"), context, metadata);
        ZoneId buildZone = safeZoneId(timezone, context.userContext().preferredTimezone().toZoneId());

        LocalDate startDate = parseDate(
                entities.get("startDate"),
                metadata,
                CalendarDraftMetadataKeys.PARSE_ERROR_START_DATE
        );
        LocalTime startTime = parseTime(
                entities.get("startTime"),
                metadata,
                CalendarDraftMetadataKeys.PARSE_ERROR_START_TIME
        );

        ZonedDateTime start = buildStart(startDate, startTime, allDay, buildZone);
        ZonedDateTime end = buildEnd(context, entities, metadata, allDay, buildZone, startDate, start);

        return new CalendarEventDraft(
                entities.getOrDefault("title", ""),
                buildDescription(context.sourceMessage().text()),
                start,
                end,
                allDay,
                timezone,
                recurrenceRule(recurrenceValue(entities)),
                entities.getOrDefault("location", ""),
                metadata
        );
    }

    private LinkedHashMap<String, String> baseMetadata(MessageHandlingContext context) {
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        metadata.put("sourceMessageId", context.sourceMessage().externalMessageId());
        metadata.put("internalMessageId", context.sourceMessage().internalMessageId());
        metadata.put("conversationId", context.sourceMessage().conversationId());
        metadata.put("channel", context.sourceMessage().channelType().name());
        return metadata;
    }

    private void captureRaw(LinkedHashMap<String, String> metadata, String key, String value) {
        if (value != null && !value.isBlank()) {
            metadata.put(key, value.trim());
        }
    }

    private Timezone resolveTimezone(
            String rawTimezone,
            MessageHandlingContext context,
            LinkedHashMap<String, String> metadata
    ) {
        if (rawTimezone == null || rawTimezone.isBlank()) {
            return context.userContext().preferredTimezone();
        }

        String normalized = rawTimezone.trim();
        try {
            ZoneId.of(normalized);
        } catch (RuntimeException exception) {
            metadata.put(CalendarDraftMetadataKeys.PARSE_ERROR_TIMEZONE, "true");
        }
        return new Timezone(normalized);
    }

    private ZoneId safeZoneId(Timezone timezone, ZoneId fallbackZone) {
        if (timezone == null) {
            return fallbackZone;
        }
        try {
            return timezone.toZoneId();
        } catch (RuntimeException exception) {
            return fallbackZone;
        }
    }

    private LocalDate parseDate(String rawDate, LinkedHashMap<String, String> metadata, String errorKey) {
        if (rawDate == null || rawDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(rawDate.trim());
        } catch (DateTimeParseException exception) {
            metadata.put(errorKey, "true");
            return null;
        }
    }

    private LocalTime parseTime(String rawTime, LinkedHashMap<String, String> metadata, String errorKey) {
        if (rawTime == null || rawTime.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(rawTime.trim());
        } catch (DateTimeParseException exception) {
            metadata.put(errorKey, "true");
            return null;
        }
    }

    private ZonedDateTime buildStart(LocalDate startDate, LocalTime startTime, boolean allDay, ZoneId zoneId) {
        if (startDate == null) {
            return null;
        }
        if (allDay) {
            return startDate.atStartOfDay(zoneId);
        }
        if (startTime == null) {
            return null;
        }
        return ZonedDateTime.of(startDate, startTime, zoneId);
    }

    private ZonedDateTime buildEnd(
            MessageHandlingContext context,
            Map<String, String> entities,
            LinkedHashMap<String, String> metadata,
            boolean allDay,
            ZoneId buildZone,
            LocalDate startDate,
            ZonedDateTime start
    ) {
        if (start == null) {
            return null;
        }

        if (allDay) {
            LocalDate explicitEndDate = parseDate(
                    entities.get("endDate"),
                    metadata,
                    CalendarDraftMetadataKeys.PARSE_ERROR_END_DATE
            );
            if (explicitEndDate != null) {
                return explicitEndDate.atStartOfDay(buildZone);
            }
            return start.plusDays(1);
        }

        Duration explicitDuration = parseDuration(entities.get("durationMinutes"), metadata);
        if (explicitDuration != null) {
            return start.plus(explicitDuration);
        }

        LocalDate endDate = parseDate(
                entities.get("endDate"),
                metadata,
                CalendarDraftMetadataKeys.PARSE_ERROR_END_DATE
        );
        LocalTime endTime = parseTime(
                entities.get("endTime"),
                metadata,
                CalendarDraftMetadataKeys.PARSE_ERROR_END_TIME
        );
        if (endTime != null) {
            LocalDate resolvedEndDate = endDate == null ? startDate : endDate;
            if (resolvedEndDate == null) {
                return null;
            }
            return ZonedDateTime.of(resolvedEndDate, endTime, buildZone);
        }

        return start.plus(context.userContext().defaultEventDuration());
    }

    private Duration parseDuration(String rawDurationMinutes, LinkedHashMap<String, String> metadata) {
        if (rawDurationMinutes == null || rawDurationMinutes.isBlank()) {
            return null;
        }
        try {
            long minutes = Long.parseLong(rawDurationMinutes.trim());
            return minutes <= 0 ? null : Duration.ofMinutes(minutes);
        } catch (NumberFormatException exception) {
            metadata.put(CalendarDraftMetadataKeys.PARSE_ERROR_DURATION, "true");
            return null;
        }
    }

    private String recurrenceValue(Map<String, String> entities) {
        String rawValue = entities.get("recurrenceRule");
        if (rawValue == null || rawValue.isBlank()) {
            rawValue = entities.get("recurrence");
        }
        return rawValue;
    }

    private RecurrenceRule recurrenceRule(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return new RecurrenceRule(rawValue.trim());
    }

    private String buildDescription(String sourceText) {
        if (sourceText == null || sourceText.isBlank()) {
            return "";
        }
        return "Created from assistant request: " + sourceText.trim();
    }
}
