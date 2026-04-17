package io.github.nadya.assistant.adapter.out.google.calendar.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.nadya.assistant.adapter.out.google.calendar.config.GoogleCalendarProperties;
import io.github.nadya.assistant.adapter.out.google.calendar.oauth.GoogleAccessTokenProvider;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class HttpGoogleCalendarClient implements GoogleCalendarClient {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(20);

    private final GoogleCalendarProperties properties;
    private final GoogleAccessTokenProvider accessTokenProvider;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HttpGoogleCalendarClient(
            GoogleCalendarProperties properties,
            GoogleAccessTokenProvider accessTokenProvider
    ) {
        this(
                properties,
                accessTokenProvider,
                HttpClient.newBuilder()
                        .connectTimeout(HTTP_TIMEOUT)
                        .build(),
                new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        );
    }

    HttpGoogleCalendarClient(
            GoogleCalendarProperties properties,
            GoogleAccessTokenProvider accessTokenProvider,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.accessTokenProvider = accessTokenProvider;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public InsertResponse insert(InsertRequest request) {
        try {
            HttpResponse<String> response = sendInsert(request, accessTokenProvider.getAccessToken());
            if (response.statusCode() == 401) {
                accessTokenProvider.invalidate();
                response = sendInsert(request, accessTokenProvider.getAccessToken());
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Google Calendar insert failed with status "
                                + response.statusCode()
                                + formatErrorDetail(response.body())
                );
            }

            GoogleCalendarInsertEnvelope envelope = objectMapper.readValue(response.body(), GoogleCalendarInsertEnvelope.class);
            if (envelope.id() == null || envelope.id().isBlank()) {
                throw new IllegalStateException("Google Calendar insert response did not contain event id");
            }

            return new InsertResponse(
                    envelope.id(),
                    envelope.htmlLink() == null ? "" : envelope.htmlLink()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Google Calendar insert response could not be parsed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google Calendar insert request was interrupted", exception);
        }
    }

    private HttpResponse<String> sendInsert(InsertRequest request, String accessToken) throws IOException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder(buildUri(request.calendarId()))
                .timeout(HTTP_TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(serializeRequest(request), StandardCharsets.UTF_8))
                .build();
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private URI buildUri(String calendarId) {
        String encodedCalendarId = URLEncoder.encode(calendarId, StandardCharsets.UTF_8);
        return URI.create(properties.baseUrl() + "/calendars/" + encodedCalendarId + "/events");
    }

    private String serializeRequest(InsertRequest request) {
        try {
            return objectMapper.writeValueAsString(toProviderRequest(request));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Google Calendar insert request could not be serialized", exception);
        }
    }

    private String formatErrorDetail(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }

        try {
            String message = objectMapper.readTree(responseBody).at("/error/message").asText("").trim();
            if (!message.isBlank()) {
                return ": " + message;
            }
        } catch (IOException ignored) {
            // Fall through to raw body snippet.
        }

        String compactBody = responseBody.replaceAll("\\s+", " ").trim();
        if (compactBody.isBlank()) {
            return "";
        }
        return ": " + compactBody;
    }

    private GoogleCalendarInsertRequest toProviderRequest(InsertRequest request) {
        GoogleCalendarEventTime start = request.allDay()
                ? GoogleCalendarEventTime.allDay(request.startDateTime().substring(0, 10), request.timezone())
                : GoogleCalendarEventTime.timed(request.startDateTime(), request.timezone());
        GoogleCalendarEventTime end = request.allDay()
                ? GoogleCalendarEventTime.allDay(request.endDateTime().substring(0, 10), request.timezone())
                : GoogleCalendarEventTime.timed(request.endDateTime(), request.timezone());
        return new GoogleCalendarInsertRequest(
                request.summary(),
                request.description(),
                request.location(),
                start,
                end
        );
    }

    record GoogleCalendarInsertRequest(
            String summary,
            String description,
            String location,
            GoogleCalendarEventTime start,
            GoogleCalendarEventTime end
    ) {
    }

    record GoogleCalendarEventTime(
            String dateTime,
            String date,
            String timeZone
    ) {
        static GoogleCalendarEventTime timed(String dateTime, String timeZone) {
            return new GoogleCalendarEventTime(dateTime, null, timeZone);
        }

        static GoogleCalendarEventTime allDay(String date, String timeZone) {
            return new GoogleCalendarEventTime(null, date, timeZone);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GoogleCalendarInsertEnvelope(String id, String htmlLink) {
    }
}
